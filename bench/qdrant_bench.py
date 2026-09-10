#!/usr/bin/env python3
"""Qdrant 1.19 filtered-ANN baseline: ingest + selectivity sweep.

  ingest -- create collection, build payload indexes BEFORE upload (filterable
            HNSW only materialises payload-block links at index build time),
            upload, wait for green.
  sweep  -- for each (predicate, ef, acorn) measure recall@10 and throughput
            against the exact GPU ground truth.

Recall is always computed against exact filtered k-NN, never another ANN run.
"""
import argparse
import json
import sys
import threading
import time
from pathlib import Path

import numpy as np
from qdrant_client import QdrantClient, models

sys.path.insert(0, str(Path(__file__).parent))
import predicates  # noqa: E402

OUT = Path("/home/ydatta/shiva-work")
COLL = "laion_bench"


def ingest(args):
    vecs = np.load(OUT / f"vectors_{args.tag}.npy", mmap_mode="r")
    attrs = np.load(OUT / f"attrs_{args.tag}.npz", allow_pickle=True)
    gt = json.load(open(OUT / f"gt_{args.tag}.json"))
    n_base = gt["n_base"]

    c = QdrantClient(url=args.url, timeout=600)
    if c.collection_exists(COLL):
        c.delete_collection(COLL)
    c.create_collection(
        COLL,
        vectors_config=models.VectorParams(size=512, distance=models.Distance.COSINE),
        hnsw_config=models.HnswConfigDiff(m=args.m, ef_construct=args.ef_construct),
        optimizers_config=models.OptimizersConfigDiff(default_segment_number=args.segments),
    )
    c.create_payload_index(COLL, "bucket", models.PayloadSchemaType.INTEGER, wait=True)
    c.create_payload_index(COLL, "similarity", models.PayloadSchemaType.FLOAT, wait=True)
    print(f"collection ready (m={args.m} ef_construct={args.ef_construct} "
          f"segments={args.segments}); payload indexes built pre-ingest")

    bucket = attrs["bucket"]
    sim = attrs["similarity"].astype(np.float32)
    t0 = time.time()
    B = args.batch
    for s in range(0, n_base, B):
        e = min(s + B, n_base)
        c.upsert(
            COLL,
            points=models.Batch(
                ids=list(range(s, e)),
                vectors=np.asarray(vecs[s:e], dtype=np.float32).tolist(),
                payloads=[{"bucket": int(b), "similarity": float(v)}
                          for b, v in zip(bucket[s:e], sim[s:e])],
            ),
            wait=False,
        )
        if (s // B) % 50 == 0:
            el = time.time() - t0
            print(f"  {e:,}/{n_base:,}  {el:.0f}s  {e/max(el,1):,.0f} pts/s", flush=True)
    print(f"upload done in {time.time()-t0:.0f}s; waiting for indexing")

    t1 = time.time()
    while True:
        info = c.get_collection(COLL)
        if info.status == models.CollectionStatus.GREEN:
            break
        print(f"  status={info.status} indexed={info.indexed_vectors_count:,} "
              f"({time.time()-t1:.0f}s)", flush=True)
        time.sleep(20)
    info = c.get_collection(COLL)
    res = {"total_ingest_s": round(time.time() - t0, 1),
           "index_wait_s": round(time.time() - t1, 1),
           "points": info.points_count,
           "indexed_vectors": info.indexed_vectors_count,
           "segments": info.segments_count}
    (OUT / "results").mkdir(exist_ok=True)
    (OUT / "results" / f"ingest_{args.tag}.json").write_text(json.dumps(res, indent=2))
    print(json.dumps(res, indent=2))


def recall_at_k(got, want, k):
    hits = sum(len(set(g[:k]) & set(w[:k])) for g, w in zip(got, want))
    return hits / (len(want) * k)


def sweep(args):
    gt = json.load(open(OUT / f"gt_{args.tag}.json"))
    attrs = np.load(OUT / f"attrs_{args.tag}.npz", allow_pickle=True)
    Q = np.load(OUT / f"queries_{args.tag}.npy").astype(np.float32)
    k, n_base = gt["k"], gt["n_base"]

    # rebuild the identical filters the ground truth used
    preds = {p["name"]: p for p in predicates.build(attrs, n_base)}

    # QdrantClient shares one connection and is not thread-safe.
    _tl = threading.local()

    def client():
        if not hasattr(_tl, "c"):
            _tl.c = QdrantClient(url=args.url, timeout=600, prefer_grpc=True)
        return _tl.c

    rows = []
    for name, spec in gt["levels"].items():
        if args.families and spec["family"] not in args.families:
            continue
        p = preds.get(name)
        if p is None:
            print(f"  {name}: no matching predicate, skipped")
            continue
        flt, want = p["qfilter"], spec["gt_ids"]

        for acorn in ([False, True] if flt is not None else [False]):
            for ef in args.efs:
                params = models.SearchParams(
                    hnsw_ef=ef,
                    acorn=models.AcornSearchParams(enable=True, max_selectivity=1.0)
                    if acorn else None,
                )

                def run(qi):
                    return [pt.id for pt in client().query_points(
                        COLL, query=Q[qi].tolist(), query_filter=flt,
                        limit=k, search_params=params).points]

                for _ in range(20):
                    run(0)

                # Throughput via the server-side batch API. A Python thread pool
                # cannot saturate Qdrant: the GIL plus per-request HTTP/JSON pins
                # aggregate QPS near the single-thread rate no matter the worker
                # count, so pooled numbers measure the client, not the engine.
                reqs = [models.QueryRequest(query=Q[qi].tolist(), filter=flt, limit=k,
                                            params=params, with_payload=False)
                        for qi in range(len(Q))]

                # Repeat and take the median: single-shot throughput on a shared
                # box swings enough between adjacent configs to invert an
                # ordering, which would make any headline number unsafe.
                qps_runs, got = [], None
                for _ in range(args.repeat):
                    acc, t0 = [], time.time()
                    for s in range(0, len(reqs), args.batch_size):
                        resp = client().query_batch_points(
                            COLL, requests=reqs[s:s + args.batch_size])
                        acc.extend([[pt.id for pt in r.points] for r in resp])
                    qps_runs.append(len(reqs) / (time.time() - t0))
                    got = acc
                qps_runs.sort()
                qps_med = qps_runs[len(qps_runs) // 2]

                # latency: strictly sequential, one query per request
                lat = []
                for qi in range(min(args.lat_samples, len(Q))):
                    t = time.time()
                    run(qi)
                    lat.append((time.time() - t) * 1000)
                lat.sort()

                row = {
                    "predicate": name, "family": spec["family"],
                    "selectivity": spec["selectivity"],
                    "actual_selectivity": spec["actual_selectivity"],
                    "n_qualifying": spec["n_qualifying"],
                    "acorn": acorn, "ef": ef,
                    "recall@10": round(recall_at_k(got, want, k), 4),
                    "qps_batched": round(qps_med, 1),
                    "qps_runs": [round(x,1) for x in qps_runs],
                    "qps_spread": round(max(qps_runs)/min(qps_runs), 3),
                    "p50_ms": round(lat[len(lat)//2], 2),
                    "p95_ms": round(lat[int(len(lat)*0.95)], 2),
                    "p99_ms": round(lat[int(len(lat)*0.99)], 2),
                }
                rows.append(row)
                print(f"  {name:<14} acorn={str(acorn):<5} ef={ef:<4} "
                      f"recall={row['recall@10']:.4f} qps={row['qps_batched']:>8.1f} "
                      f"p50={row['p50_ms']:>7.2f}ms", flush=True)

    (OUT / "results").mkdir(exist_ok=True)
    label = f"{args.tag}{('_' + args.label) if args.label else ''}"
    with open(OUT / "results" / f"qdrant_{label}.json", "w") as f:
        json.dump({"tag": args.tag, "label": args.label, "m": args.m,
                   "ef_construct": args.ef_construct, "k": k, "nq": len(Q),
                   "rows": rows}, f, indent=2)
    print(f"wrote results/qdrant_{label}.json  ({len(rows)} rows)")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("cmd", choices=["ingest", "sweep"])
    ap.add_argument("--tag", default="10m")
    ap.add_argument("--url", default="http://localhost:6333")
    ap.add_argument("--batch", type=int, default=2000)
    ap.add_argument("--m", type=int, default=16)
    ap.add_argument("--ef-construct", type=int, default=100)
    ap.add_argument("--segments", type=int, default=4)
    ap.add_argument("--batch-size", type=int, default=100)
    ap.add_argument("--repeat", type=int, default=3)
    ap.add_argument("--lat-samples", type=int, default=100)
    ap.add_argument("--label", default="")
    ap.add_argument("--families", nargs="+", default=None)
    ap.add_argument("--efs", type=int, nargs="+", default=[64, 128, 256])
    a = ap.parse_args()
    (ingest if a.cmd == "ingest" else sweep)(a)
