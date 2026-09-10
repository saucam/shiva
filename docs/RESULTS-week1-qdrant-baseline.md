# Week 1 Results: Qdrant 1.19.1 Filtered-ANN Baseline

**Status:** complete — 1M and 9.6M
**Date:** 2026-09-10
**Verdict: Gate 0 PASSED.** The wedge is real, it grows with corpus size, and it is not tunable away.

---

## Setup

| | |
| --- | --- |
| Corpus | LAION-400M shards 0–9, **9,646,707** vectors, 512-dim fp16, unit-norm |
| Baseline | Qdrant 1.19.1, Docker, 24 CPU / 64 GB, NVMe storage |
| Index | HNSW m=16, ef_construct=100, converged to 12 segments, payload indexes built **pre-ingest** |
| Ingest | 2,520 s total (5,070 pts/s) + 560 s index build; 23 GB on disk |
| Queries | 1000 held-out vectors, never indexed (no self-matches) |
| Ground truth | Exact filtered k-NN, GPU brute force, RTX 4090 |
| Throughput | Server-side batch API, median of 3 runs |

Fairness checks performed before trusting any number:

- `payload_m` defaults to `m`=16 and is non-zero, so Qdrant genuinely builds filterable-HNSW payload-block links on both indexed fields. The premise under test is actually active.
- Qdrant's exact count matches the numpy ground-truth mask for all 22 predicates (two differ by 1 and 3 points respectively — float32 boundary rounding, ~0.002%, immaterial to recall@10).
- Collection settled to `green` with all 9,636,707 vectors indexed before any measurement.

### Predicate families

Selectivity is held constant while correlation with vector position varies. That isolation is the experiment.

- **`bucket`** — uniform random int in [0,1000). Zero correlation. *This is what most published filtered-ANN benchmarks measure.*
- **`sim`** — threshold on CLIP image/caption similarity. Derived *from* the embedding, so qualifying points cluster in vector space.
- **`conj`** — `bucket AND sim`. Two mid-selectivity predicates whose conjunction has no dedicated payload block.

Measured selectivity tracks target within 0.05% in every cell.

---

## Headline — and the correction the control forced

At Qdrant's **default** `m`=16, throughput at recall@10 ≥ 0.95 (best config across ef ∈ {64,128,256} × ACORN):

| selectivity | uncorrelated | correlated | **conjunction** |
| ---: | ---: | ---: | ---: |
| 0.001 | 2971 | 2936 | 2118 |
| **0.005** | 859 | 1052 | **98** |
| **0.010** | 888 | 884 | **75** |
| 0.050 | 833 | 774 | 590 |
| 0.100 | 666 | 664 | 807 |

That looks like an 11.8× collapse at 1% selectivity. **It is not a safe headline.**

A control run at `m`=32 / `ef_construct`=256 — the objection I expected a reviewer to raise — substantially narrows it:

| selectivity | m=16 bucket | m=16 conj | ratio | m=32 bucket | m=32 conj | ratio |
| ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| 0.005 | 859 | 98 (ef64,acorn) | 8.7× | 1123 | 59 (ef64,acorn) | 19.1× |
| **0.010** | 888 | 75 (ef64,acorn) | **11.8×** | 894 | **421 (ef256)** | **2.1×** |
| 0.050 | 833 | 590 (ef128) | 1.4× | 528 | 779 (ef64) | 0.7× |
| 0.100 | 666 | 807 (ef64) | 0.8× | 527 | 550 (ef64) | 1.0× |

With a denser graph and a wide beam, `conj@0.01` clears the 0.95 floor **without ACORN at all** — 0.9652 recall at 421 QPS. The honest gap at 1% selectivity is therefore **2.1×, not 11.8×**.

**The "QPS at a fixed recall floor" metric is fragile and should not be the headline.** It is a step function of whether a configuration happens to land above or below an arbitrary threshold. `conj@0.005` at m=32/ef=256 scores 0.9453 — missing 0.95 by 0.5pp, roughly 2σ of the ±0.3pp sampling error — which throws that cell onto ACORN and reports 19.1×. Flip that one measurement and the number changes by an order of magnitude. The recall curves below are the real evidence.

Figure: `results/gate0_10m.png` (m=16).

---

## Result 1 — The conjunction penalty doubles from 1M to 9.6M

recall@10, ef=64, ACORN off:

| selectivity | 1M conj penalty | 9.6M conj penalty | 9.6M conj recall |
| ---: | ---: | ---: | ---: |
| 0.005 | −0.41pp | **−17.68pp** | 0.8056 |
| 0.010 | −8.76pp | **−16.95pp** | 0.8229 |
| 0.050 | −5.41pp | −6.12pp | 0.9270 |
| 0.100 | −2.62pp | −2.90pp | 0.9503 |

This is the most important number in the run.
The penalty is not a fixed tax — it **grows with corpus size**, which means it gets worse in production rather than better.
Recall of 0.80 at 1% selectivity is not a degradation, it is a broken result.

## Result 2 — A denser graph plus a wide beam narrows the gap but does not close it

Conjunction recall without ACORN, across both index configurations:

| selectivity | m=16 ef=64 | m=16 ef=256 | m=32 ef=64 | m=32 ef=256 |
| ---: | ---: | ---: | ---: | ---: |
| 0.005 | 0.8056 | 0.9075 | 0.8517 | **0.9453** |
| 0.010 | 0.8229 | 0.9368 | 0.8703 | **0.9652** |
| 0.050 | 0.9270 | 0.9853 | 0.9510 | 0.9925 |
| 0.100 | 0.9503 | 0.9902 | 0.9668 | 0.9939 |

This is the most useful table in the run, and it is more nuanced than the m=16 data alone implied.

- Doubling `m` buys a consistent **+3 to +5pp** at fixed ef. It genuinely helps.
- Quadrupling ef buys **+9 to +11pp**, considerably more than `m` does.
- **Both together are required** to clear 0.95 at 1% selectivity — and even then 0.5% selectivity still misses at 0.9453.
- Conjunction recall sits below the uncorrelated case in **every one of the eight cells** in the 0.005–0.01 band. The effect is robust to index configuration; only its magnitude moves.

What it costs to buy that: `m`=32/`ef_construct`=256 took **4,520 s to ingest and 2,321 s to index**, versus 2,520 s and 560 s at `m`=16 — **1.8× ingest and 4.1× index build**. The denser index is also slower to *search* almost everywhere (`bucket@0.05`: 833 → 528 QPS), so it is not a free upgrade; it trades general throughput for conjunction correctness.

## Result 3 — The ACORN tax explodes at scale

| predicate | Δ recall | QPS cost @1M | **QPS cost @9.6M** | p50 cost @9.6M |
| --- | ---: | ---: | ---: | ---: |
| conj@0.05 | +7.05pp | 3.95× | **15.29×** | 6.88× |
| conj@0.1 | +4.64pp | 4.09× | **15.55×** | 6.78× |
| conj@0.01 | +17.50pp | 3.51× | **8.59×** | 2.14× |
| bucket@0.25 | +2.50pp | 1.94× | 8.54× | 5.49× |

ACORN does restore recall — to 0.995+ nearly everywhere. It is not broken.
But the price rose roughly 4× when the corpus grew 10×, reaching **15.5×** throughput.
Note the tax is now substantial even for *uncorrelated* single predicates (8.5×), so this is not a conjunction-only cost.

## Result 4 — A bad cardinality estimate makes Qdrant pick the wrong plan

Plan selection lives in `lib/segment/src/index/hnsw_index/hnsw/read_view/dispatch.rs:122-163`.
It is a three-branch heuristic, not a cost model:

```rust
if query_cardinality.max < full_scan_threshold  -> exact plain scan
if query_cardinality.min > full_scan_threshold  -> filtered HNSW graph
else                                            -> sample_check_cardinality(...)
```

Both comparisons key off an **estimate**, never the true count.
In the uncertain middle the plan is decided by **random sampling**, so identical queries can receive different plans.

Measured at 9.6M, `full_scan_threshold` = 10,000 (default):

| predicate | true | estimated | error | plan by *true* | plan by *estimate* |
| --- | ---: | ---: | ---: | --- | --- |
| `bucket@0.001` | 9,528 | 9,547 | +0.2% | exact | exact ✓ |
| `sim@0.001` | 9,624 | 37,515 | **+290%** | exact | **graph ✗** |
| `sim@0.005` | 48,115 | 68,022 | +41% | graph | graph |

`bucket@0.001` and `sim@0.001` match nearly the same number of points, both under threshold.
The integer field estimates to within 0.2% and correctly takes the exact path.
The float field overestimates 3.9× and is routed to the graph, forfeiting an exact answer it was entitled to.

This is the RFC-001 "no plan space" claim with a measurement attached, and it explains the shape of Result 1: the correlated predicate suffers *two* compounding failures — mis-estimation routing it to the approximate path, then correlation penalising that path.

Corroborating detail: `conj@0.001` (9,648 points, under threshold) shows **no** penalty at all (+0.18pp), while `conj@0.005` (48,566 points, over threshold) collapses to 0.8056. The damage begins exactly at the plan boundary.

---

## Gate 1 pre-check — memory bandwidth

Measured on an idle box (Qdrant at 0.5% CPU), 4 GiB buffer, AVX2 streaming read:

| threads | GB/s | GB/s/thread |
| ---: | ---: | ---: |
| 1 | **14.4** | 14.42 |
| 2 | 31.0 | 15.50 |
| 4 | 50.0 | 12.50 |
| 8 | 51.7 | 6.46 |
| 24 | 54.1 | 2.25 |

RFC-001 Gate 1 asks for ≥5 GB/s per P-core. Single-thread delivers **14.4 GB/s — 2.9× the gate**, saturating around 54 GB/s aggregate at 4–8 threads.

At 512-dim 4-bit (256 B/vector) that is ~56M vectors/sec/core and ~211M/sec aggregate, against a Gate 1 target of 20M/sec/core.

**Caveat:** this is a streaming read with a cheap SAD reduction, not a real RaBitQ kernel. It establishes the *ceiling*, not the achieved rate. The napkin figure for `vpdpbusd` (~8 loads + 8 dot-products per 512-dim vector, ~16–20 cycles, ~250M vec/s compute-bound) says memory should remain the binding constraint, but that only holds if the kernel is written well. Gate 1 still has to be earned in week 2.

---

## Methodology — three bugs found and fixed

Recording these because each would have produced confident, wrong numbers.

**Throughput was measuring Python, not Qdrant.** A 16-thread `ThreadPoolExecutor` returned a flat ~250 QPS across *every* selectivity and ef. Sequential p50 of 6.7 ms implies ~148 QPS single-threaded, so 16× concurrency bought 1.8× — the GIL and per-request HTTP/JSON were the bottleneck, not the engine. gRPC plus the server-side batch API moved the range to 45–3000 QPS and made it respond to configuration. A single client shared across threads also fails outright with `EBADF`.

**One NaN silently emptied every correlated predicate.** LAION's `similarity` column carries ~0.15% NaN and out-of-range junk (observed max 2336 for what must be a cosine). A single NaN makes `np.quantile` return NaN, `sim >= NaN` is universally false, and all 14 `sim`/`conj` predicates returned empty — as a clean run, not an error. Invalid values now map to −1.0 at prep so numpy and Qdrant agree on which rows they are.

**Ground truth did not match Qdrant's distance handling.** Qdrant re-normalises on ingest for `Distance.COSINE`; the ground truth was doing raw inner product on fp16-rounded vectors whose norms drift ~1e-3. Not fatal for family-vs-family comparison, since it cancels, but it would have made every absolute number quietly wrong.

Two further runs were discarded rather than reported: an estimator diagnostic executed against a mid-ingest collection (counts changing between calls), and a bandwidth measurement taken while Qdrant pinned 19 cores.

---

## Caveats

- **No AVX-512 on this CPU.** Absolute throughput will not transfer to a server; Sapphire Rapids/Genoa have AVX-512, VNNI-512 and roughly double the bandwidth. Re-run before publishing.
- **`nq`=1000** gives recall@10 a sampling error of roughly ±0.3pp. The 16–18pp effects are far outside it; sub-1pp deltas in the tables are at the edge of resolution and should not be interpreted.
- **The m=32 control has now been run, and it moved the answer.** This was the right call: the pre-control headline (11.8×) was an artifact of Qdrant's default `m`=16 and did not survive. Anything measured only at defaults should be treated as provisional until a tuned control exists.
- **Throughput at a fixed recall floor is a fragile metric.** It is a step function of whether a config lands above or below the threshold; a single cell missing 0.95 by 0.5pp swings the reported ratio from 2.1× to 19.1×. Prefer the full recall-vs-selectivity curves.
- **`full_scan_threshold` left at default.** Raising it would push more queries onto the exact path and mask the effect; that is a legitimate mitigation Qdrant users can apply, at a latency cost that has not been measured here.
- Single node, no replication, no quantization.

---

## Verdict

**Gate 0 passes — but by a much smaller margin than the default-configuration data suggested, and not for the reason RFC-001 predicted.**

The RFC bet that filtered search degrades in the mid-selectivity band. Single-attribute filtering on an uncorrelated column — the case nearly every published benchmark measures — costs Qdrant almost nothing: 0.9977 → 0.9637 across three orders of magnitude. Had that been the whole story, the correct move would have been to stop.

What survives contact with the m=32 control:

1. **Conjunctions of mid-selectivity predicates are the weak point,** and the effect is robust — conjunction recall is below the uncorrelated case in all eight (m, ef) cells tested in the 0.005–0.01 band.
2. **The penalty grows with corpus size** — it roughly doubled from 1M to 9.6M at fixed configuration. This is the single most important property, because it means the gap widens in production rather than closing.
3. **Correctness there is not free.** Clearing a 0.95 floor at 1% selectivity requires *both* `m`=32 and ef=256, costing 1.8× ingest, 4.1× index build, and ~2× lower throughput than the same index achieves on a single predicate. At 0.5% selectivity even that configuration misses.
4. **The planner has no cost model**, mis-estimates float ranges by 290%, and demonstrably routes queries to the wrong plan. Unlike the recall numbers, this is a structural property that no amount of index tuning changes.

What does **not** survive: the "11.8× collapse" headline. That was an artifact of Qdrant's default `m`=16. Properly tuned, the gap at 1% selectivity is **2.1×**. Anyone publishing the 11.8× number would have been correctly accused of benchmarking a straw configuration.

So the defensible claim is narrower than it looked an hour ago:

> On conjunctive filtered queries at 10M scale, Qdrant needs a substantially more expensive index (1.8× ingest, 4.1× build, ~2× lower search throughput) to stay correct, still misses at 0.5% selectivity, and the gap grows with corpus size. Its planner has no cost model and provably mis-routes float-range predicates.

The bar for the kernel is correspondingly harder: **beat 421 QPS at recall ≥0.95 on `conj@0.01` at 9.6M** — 5.6× tougher than the pre-control figure — while also beating a 2,520 s ingest and 560 s index build.

**Recommendation: proceed to week 2**, with expectations reset. The wedge is real but it is a ~2× throughput advantage against a *tuned* competitor, not an order of magnitude. Result 4 (no cost model) is the more durable asymmetry and deserves more weight in the design than the recall numbers. If the partition-first kernel cannot clear 421 QPS at that recall, the honest move is to stop at Gate 2 rather than argue with the measurement.
