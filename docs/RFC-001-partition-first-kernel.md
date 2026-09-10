# RFC-001: Partition-First Retrieval Kernel — 10-Week Falsification Experiment

**Status:** proposed
**Date:** 2026-09-09
**Author:** Yash Datta (with Claude)

---

## 0. What this document is

This is not a plan to build a vector database.
It is a plan to spend ten weeks finding out whether a partition-first retrieval engine is worth building at all, and to fail fast and cheaply if it is not.

The deliverable is a single-node Rust library plus one benchmark plot.
No server, no gRPC, no cluster, no persistence format stability, no API design.

---

## 1. The thesis being tested

Every graph-first vector database — Qdrant, Weaviate, Milvus's HNSW path, pgvector — is organized as:

> The index is primary, physical layout is derived from it, and predicates are patched in afterward.

The proposed inversion:

> The **partition** is primary.
> "Which vectors are near q" and "which vectors satisfy P" are the same operation: scoring a partition.
> The graph is demoted from being the index to being a routing structure over centroids.

A partition holds 8k–64k vectors and carries four things:

1. A centroid plus a small representative set, for vector-space routing.
2. Per-column zone maps — min/max, roaring bitmaps, count-distinct — for predicate pruning.
3. Contiguously laid out quantized vectors, for SIMD and GPU scan.
4. A content hash, making it immutable, cacheable, and independently shippable.

Query planning becomes one utility function over partitions:

```
score(partition | q, P) = f(centroid_distance, predicate_survival_estimate)
```

Probe in descending score order until a recall or latency budget is exhausted.
Pruning and routing become the same mechanism, so the mid-selectivity band that destroys filterable HNSW becomes the band this design is best at rather than worst at.

### What is explicitly NOT claimed

Partition scan does not beat HNSW on unfiltered search at high recall.
It loses there by roughly 5–10x per core, and it always will.
HNSW at recall 0.99 over 10M vectors touches a few thousand vectors; a 2% probe touches 200k.
Fewer operations wins when operations are the bottleneck.

The bet is that operations stop being the bottleneck in four regimes:

- **Filtered queries**, where the graph degrades catastrophically and scan degrades gracefully.
- **Parallel hardware**, where the graph's serial pointer-chasing cannot fill SIMD lanes or GPU warps.
- **Update-heavy workloads**, where the graph requires global rebuild and partitions require only boundary rebalancing.
- **Cost-constrained deployments**, where 4 bits per dimension fits on SSD and fp32 plus graph does not.

If the experiment cannot demonstrate a decisive win in regime one, the rest does not matter.

---

## 2. Prior art, honestly stated

This must be read before starting, because it bounds what is novel.

| Work | What it already does | What it leaves open |
| --- | --- | --- |
| [Query-aware Routing for Filtered ANN](https://arxiv.org/pdf/2606.19898) (2026) | ML router scoring IVF partitions by joint proximity + predicted filter survival. Beats ACORN/DiskANN. | No new index structure. No updates, tiering, or multi-tenancy. ML router has training/drift/calibration cost. |
| [SPFresh](https://dl.acm.org/doi/10.1145/3600006.3613166) (SOSP '23) | LIRE protocol: in-place partition updates by rebalancing only boundary vectors. | Not integrated with filtering or a cost model. |
| [Curator](https://arxiv.org/abs/2401.07119) | Tenant-aware hierarchical k-means with Bloom filters in a shared tree. | In-memory only. Not disk/object-store tiered. |
| [Extended RaBitQ](https://github.com/VectorDB-NTU/Extended-RaBitQ) (SIGMOD '25) | B-bit quantization with sharp theoretical error bounds. | Reference impl; kernels not tuned for VNNI or consumer hardware. |
| turbopuffer / LanceDB | Object-storage-first, partition/fragment-scoped indexes, ~23x cost reduction. | Closed (turbopuffer). LanceDB routing is proximity-only, not predicate-aware. |

**Conclusion:** the routing idea is published, so this is not a research contribution.
Every individual component exists in the literature.
The open space is the *systems* contribution — one partition abstraction serving filtering, updates, tiering, GPU batching, and multi-tenancy simultaneously, with a calibrated analytic cost model instead of a learned router.
Nobody has shipped that.

If the goal is a paper, stop now; the angle is taken.
If the goal is an engine, this is de-risked rather than blocked.

---

## 3. Hardware reality and its consequences

Measured on the target machine, 2026-09-09:

| Property | Value | Consequence for the design |
| --- | --- | --- |
| CPU | i9-13900K, 8 P-cores + 16 E-cores (32 threads) | Heterogeneous cores wreck benchmark variance. Pin to P-cores. |
| SIMD | AVX2, **AVX-VNNI**, FMA, F16C. **No AVX-512.** | `VPOPCNTDQ` unavailable, so 1-bit scan needs AVX2 pshufb/Harley-Seal. **Primary kernel is 4-bit on `vpdpbusd`.** |
| RAM | 125 GB total, ~71 GB available | Comfortable for 10M scale. |
| GPU | RTX 4090, 24 GB, CUDA 13.0 | GPU leg viable at 10M. 100M at 4-bit would not fit VRAM. |
| NVMe (`/home`, `/`) | WD_BLACK SN850X. 2.9 GB/s seq, 63.6 µs random-4K p50 (135.6 µs p99), ~15.7k IOPS at QD1. 174 GB + 93 GB free. | The only place a measured working set may live. |
| HDD (`/data`) | Toshiba 7200rpm SATA, 12.7 TB. 137 MB/s seq, **6.56 ms** random-4K p50 (18.9 ms p99), ~152 IOPS. 12 TB free. | Cold storage for corpora and artifacts. Also a deliberate slow-storage tier — see §3.1. |
| Toolchain | rustc 1.95.0, kernel 6.8 with io_uring | Ready. |

Storage placement rule, to be enforced in the harness rather than remembered:
raw corpora, ground-truth files, Qdrant snapshots, and result artifacts live on `/data`;
every byte either system touches during a measured run lives on NVMe.
Disk pressure is no longer a constraint on dataset size — 12 TB removes it — but NVMe free space still bounds the measured working set, and VRAM (24 GB) bounds the GPU-resident set.

Two hazards to write into the methodology now, not later:

**Portability caveat.** Numbers measured on Raptor Lake will not transfer to a server. Sapphire Rapids and Genoa have AVX-512 with VNNI-512 and roughly double the memory bandwidth. Any published claim must state the platform and, ideally, be re-run on a rented server instance before it leaves the lab.

**Core pinning.** All kernel microbenchmarks pin to P-cores with `taskset`, hyperthreading disabled for the measured set, and turbo behaviour recorded. E-core contamination will otherwise produce 2–3x swings that look like real effects.

### 3.1 The HDD is a free experiment, not just a junk drawer

The 12 TB spinning disk measured above is the cheapest available proxy for high-latency storage, and it tests the part of the thesis that the NVMe cannot.

The argument: HNSW traversal issues a chain of **serially dependent** random reads — hop *N+1*'s address is unknown until hop *N* returns — typically 10–30 deep at high recall.
Partition scan issues a **known set of independent** reads, all addressable upfront, all issuable at once.
On 63 µs NVMe that distinction barely registers.
At 6.5 ms per seek it dominates everything: a 20-hop traversal cannot go faster than 130 ms no matter how much concurrency is available, while 200 partition reads can be queued together and reordered by the elevator.

This is the same structural property that decides whether a design can live on object storage.
S3 GET latency is 20–50 ms with effectively unlimited parallelism — fatal to serial dependency chains, survivable for parallel-issue designs.
So the HDD run is a directional test of the disaggregation claim without renting anything or writing an S3 backend.

**State the caveat honestly in any writeup:** an HDD has one actuator, so it cannot actually service 200 reads concurrently. NCQ seek reordering recovers maybe 2–5x, not the ~200x that real object storage would. The HDD therefore *understates* how far ahead a parallel-issue design would be on S3. It establishes the direction of the effect, not its magnitude.

Scheduled as an add-on to week 7, since it needs no new code — only cold-cache runs of both systems with the working set staged on `/data`.

---

## 4. Scope

### In scope

- Extended-RaBitQ encode/decode with 4-bit primary and 1-bit secondary.
- AVX-VNNI and AVX2 scan kernels, plus a CUDA scan kernel.
- Balanced hierarchical k-means partitioner.
- Zone maps over attribute columns: min/max, roaring bitmaps, cardinality.
- The analytic cost model and partition router.
- A two-stage pipeline: quantized scan then full-precision rerank of the top candidates.
- A benchmark harness that measures Qdrant and this kernel identically.

### Out of scope, deliberately

Server, wire protocol, WAL, crash recovery, replication, consensus, multi-tenancy, S3 tiering, sparse/BM25/hybrid, multi-vector and late interaction, an update path beyond a stub, and any public API.

Each of these is defensible in the eventual system and each is a way to spend ten weeks not answering the question.

---

## 5. The one plot that decides everything

X axis: predicate selectivity, swept across 0.1%, 0.5%, 1%, 5%, 10%, 25%, 50%, 100%.
Y axis: QPS at fixed recall@10 ≥ 0.95, single node, all cores.
Two lines: Qdrant 1.19 with filterable HNSW + ACORN, and this kernel.

**Success** is Qdrant's line collapsing through the 1–10% band while this kernel's line stays flat or rises as selectivity tightens.

**Failure** is Qdrant staying flat — meaning filterable HNSW plus ACORN is good enough and the wedge does not exist.

Both outcomes are worth ten weeks.
The second one saves two years.

Secondary plots, only if the primary succeeds: recall-vs-QPS at fixed selectivity, cost per query in bytes touched, index build time, GPU versus CPU throughput, and latency under concurrent write load.

---

## 6. Schedule and kill gates

The weeks are ordered so the thesis can die as early as possible.

### Week 1 — Harness and baseline, before writing any kernel

Stand up Qdrant 1.19 locally, ingest the dataset, build filterable HNSW with payload indexes created *before* ingest, and measure the full selectivity sweep.
Compute exact filtered ground truth by GPU brute force on the 4090.
Build the measurement harness — fixed query set, warm cache protocol, pinned cores, p50/p95/p99, recall computed against exact ground truth.

Deliverable: **the Qdrant baseline curve.**
If Qdrant does not visibly degrade in the mid-selectivity band, invoke Gate 0 and stop.
This is the highest-value week in the schedule and it involves writing no novel code.

### Week 2 — Scan kernel

Extended-RaBitQ 4-bit encoder. AVX-VNNI kernel via `vpdpbusd`, AVX2 fallback, scalar reference for correctness.
Evaluate the existing `rabitq` and `rabitq-rs` crates before writing from scratch; treat them as candidates, not dependencies, until benchmarked.

> **Gate 1: ≥ 5 GB/s per P-core of quantized scan throughput.**
> Stated in bandwidth rather than vectors/sec so it stays dimension-independent — on the 512-dim LAION primary that is 256 bytes per vector, so ~20M vectors/sec/core; at 768 dims it is ~13M/sec/core.
> The point of the gate is *which resource is the limit*, not the absolute figure: `vpdpbusd` should reach roughly 200M vectors/sec/core compute-bound, so a correct kernel must be memory-bandwidth-bound.
> If it lands compute-bound well under this, the cost argument underlying the whole thesis is wrong. Stop.

### Week 3 — Partitioner

Balanced hierarchical k-means, target 8k–64k vectors per partition.
Measure partition quality: recall achievable at a given probe fraction, size skew, and centroid separation.

### Week 4 — Zone maps and predicate evaluation

Per-partition, per-column min/max, roaring bitmaps for low-cardinality columns, and count-distinct.
Vectorized predicate evaluation over surviving candidates.
Calibrate the survival estimator against measured truth — an estimator that is confidently wrong is worse than no estimator.

### Week 5 — The router

The analytic cost model and partition scoring function.
Deliberately **not** an ML router: the prior-art paper uses one, and an analytic model that can be reasoned about, calibrated online, and debugged is the actual differentiator.
Include the recall-budget stopping rule.

### Week 6 — End to end, and the plot

Wire it together. Two-stage scan and rerank. Run the identical sweep from week 1.

> **Gate 2:** the primary plot shows the crossover.
> This is the go/no-go for the entire programme.

### Week 7 — GPU leg

CUDA scan kernel, batched partition scan, host/device transfer strategy.
The claim to test is that GPU widens the gap specifically because scan batches and graph traversal does not.

### Week 8 — Updates

SPFresh-style LIRE boundary rebalancing, stub quality.
Measure freshness-versus-latency under sustained write load and compare against Qdrant's segment optimizer doing whole-graph rebuilds.

### Week 9 — Adversarial hardening

Correlated attributes, where the predicate correlates with vector position — the case that breaks naive survival estimation.
Conjunctions of mid-selectivity predicates. Skewed partitions. Cold cache. Queries chosen to be hard by the [α-Hardness generator](https://arxiv.org/abs/2606.14193) rather than by uniform sampling.

> **Gate 3:** does the week-6 result survive contact with adversarial inputs?

### Week 10 — Write up and decide

Reproducible benchmark repo, honest methodology section including the AVX-512 caveat, and a written go/no-go on library versus database versus shelve.

---

## 7. Dataset

Requirements: ~10M vectors, real attributes with realistic attribute-vector correlation, and published baselines if available.
1M is too easy — everything works at 1M.

10M is the right scale for the **head-to-head**, and the binding constraint is the baseline, not this engine: Qdrant at 100M/768d needs fp32 plus graph, roughly 350–400 GB, which does not fit the NVMe free space. It has nothing to do with what `/data` can hold.

That asymmetry is itself worth measuring as a secondary result.
At 4 bits per dimension, 100M/768d partitions come to ~38 GB and fit comfortably on NVMe — and the raw corpus stages on `/data` without strain.
So a single-system 100M scaling run demonstrates this engine operating at a scale where Qdrant physically cannot be stood up on the same box.
Schedule it only after Gate 2; it is a supporting result, not a load-bearing one, and it is not a head-to-head.

This was resolved on 2026-09-09 rather than deferred to week 1. The local `/data/hf-cache` holds 319 GB but nothing relevant — it is entirely Highflame red-team and prompt-injection corpora, so there is no shortcut and the primary dataset must be downloaded.

**Primary: LAION-400M, first 10 shards.**

- 410 shards of ~1M rows, so shards 0–9 give exactly 10M without subsampling.
- Per shard: `img_emb_{N}.npy`, `text_emb_{N}.npy`, `metadata_{N}.parquet`, served over plain HTTP from `deploy.laion.ai` with no auth and no gating. (Verified: the Cohere Wikipedia embedding datasets now return HTTP 401 and are unusable without credentials.)
- **512-dim float32** — 20.5 GB raw for 10M, ~2.6 GB at 4-bit. Fits NVMe with room for the Qdrant baseline alongside.
- Metadata columns: `url`, `caption`, `NSFW`, `similarity` (f32), `width`, `height`, `license`.

The attribute set is the reason to prefer this over the alternatives, and it is worth being explicit about why.
`license` is roughly independent of the embedding — the easy case.
`width` and `height` are weakly correlated — range predicates over a skewed distribution.
`NSFW` and `similarity` are **derived from the CLIP embedding itself**, making them maximally correlated with vector position.
That is precisely the case that breaks naive filter-survival estimation, and it is the week-9 adversarial scenario available for free inside the primary dataset rather than needing to be synthesized.

**Secondary: YFCC-10M** from the big-ann-benchmarks filtered track, for comparability against published leaderboard numbers only.
It is **192-dim uint8** with tags as CSR sparse matrices — about 1.9 GB total.
Do not make it primary. At 192 dims of uint8 the corpus fits in RAM trivially and quantization buys 8→4 bits rather than 32→4, so it would systematically understate both the bandwidth and the compression argument the thesis rests on.

**Third: the α-Hardness generator** from arXiv 2606.14193, for the controlled selectivity sweep.

Use LAION as the load-bearing result. Do not build the case on synthetic data alone; correlated real attributes are where these designs actually break.

---

## 8. Repository layout

Rust workspace, new tree, leaving the existing Scala untouched until there is a reason to delete it.

```
shiva/
├── core/                    # existing Scala — frozen, not extended
└── rust/
    ├── shiva-quant/         # RaBitQ encode/decode + CPU kernels
    ├── shiva-partition/     # balanced k-means, partition format
    ├── shiva-zonemap/       # zone maps, roaring bitmaps, predicate eval
    ├── shiva-router/        # cost model, scoring, stopping rule
    ├── shiva-gpu/           # CUDA kernels via cudarc (feature-gated)
    ├── shiva-engine/        # assembly: two-stage query path
    └── shiva-bench/         # harness, Qdrant driver, ground truth, plots
```

Likely dependencies: `simsimd` for reference distance kernels, `roaring`, `cudarc`, `rayon`, `memmap2`, `criterion`, and the `qdrant-client` crate for the baseline driver.

---

## 9. Explicit risks

**The wedge may not exist.** Qdrant ships fast and ACORN may already be good enough. Week 1 is designed to find this out before any investment.

**The competitors are real and funded.** turbopuffer and LanceDB are structurally adjacent. "Them, but open source" is not a thesis. The differentiator must be the analytic cost model plus the single partition abstraction spanning filtering, updates, and tiering.

**"Faster vector DB" is not a business.** pgvector plus DiskANN is commoditizing the floor. If this becomes a product, the framing is per-tenant filtered agentic retrieval under constant writes, not raw ANN throughput.

**Benchmark self-deception is the default outcome.** Every vector search benchmark published by a vendor favours the vendor. Mitigations: exact ground truth rather than approximate, adversarial query selection, identical hardware and cache state for both systems, Qdrant tuned by its own documentation rather than left at defaults, and publishing the harness so the numbers can be attacked.

**Ten weeks is optimistic if any week slips.** The gates exist so that slipping produces an early stop rather than a late one.
