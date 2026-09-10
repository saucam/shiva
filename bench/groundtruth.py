#!/usr/bin/env python3
"""Exact filtered k-NN ground truth on GPU.

Brute force, not approximate -- recall is meaningless if the reference is itself
approximate. Vectors are unit-norm, so inner product ranks identically to cosine.

Predicates come from predicates.py, which is deterministic given the attribute
table, so qdrant_bench.py rebuilds the identical filters without needing the
masks serialised here.
"""
import argparse
import json
import sys
import time
from pathlib import Path

import numpy as np
import torch

sys.path.insert(0, str(Path(__file__).parent))
import predicates  # noqa: E402

OUT = Path("/home/ydatta/shiva-work")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", default="10m")
    ap.add_argument("--k", type=int, default=10)
    ap.add_argument("--nq", type=int, default=1000)
    ap.add_argument("--holdout", type=int, default=10000)
    ap.add_argument("--families", nargs="+", default=["bucket", "sim", "conj"])
    args = ap.parse_args()

    dev = "cuda"
    vecs = np.load(OUT / f"vectors_{args.tag}.npy", mmap_mode="r")
    attrs = np.load(OUT / f"attrs_{args.tag}.npz", allow_pickle=True)
    n_total = vecs.shape[0]
    n_base = n_total - args.holdout
    print(f"corpus {n_total:,}  base {n_base:,}  holdout {args.holdout:,}")

    def renorm(a):
        """Match Qdrant: it re-normalises on ingest for Distance.COSINE.

        Storage is fp16, so norms drift ~1e-3 off unit after rounding. Doing raw
        inner product here while Qdrant normalises would let near-ties rank
        differently and show up as a small recall deficit in every cell. It would
        cancel in family-vs-family comparisons, but it would make the absolute
        numbers quietly wrong, so normalise on the same fp16 values Qdrant saw.
        """
        n = np.linalg.norm(a, axis=1, keepdims=True)
        n[n == 0] = 1.0
        return a / n

    rng = np.random.default_rng(7)
    qidx = np.sort(rng.choice(np.arange(n_base, n_total), size=args.nq, replace=False))
    Qnp = renorm(np.asarray(vecs[qidx], dtype=np.float32))
    Q = torch.from_numpy(Qnp).to(dev)
    np.save(OUT / f"queries_{args.tag}.npy", np.asarray(vecs[qidx], dtype=np.float16))

    preds = predicates.build(attrs, n_base, families=tuple(args.families))
    print(f"{len(preds)} predicates across families {args.families}")

    levels = {}
    for p in preds:
        t0 = time.time()
        idx = np.flatnonzero(p["mask"])
        if len(idx) == 0:
            print(f"  {p['name']}: EMPTY, skipped")
            continue

        best_s = torch.full((args.nq, args.k), -1e30, device=dev)
        best_i = torch.zeros((args.nq, args.k), dtype=torch.int64, device=dev)
        CH = 2_000_000
        for s in range(0, len(idx), CH):
            sub = idx[s:s + CH]
            V = torch.from_numpy(renorm(np.asarray(vecs[sub], dtype=np.float32))).to(dev)
            sims = Q @ V.T
            kk = min(args.k, sims.shape[1])
            cs, ci = torch.topk(sims, kk, dim=1)
            gi = torch.from_numpy(sub).to(dev)[ci]
            best_s = torch.cat([best_s, cs], dim=1)
            best_i = torch.cat([best_i, gi], dim=1)
            cs2, ci2 = torch.topk(best_s, args.k, dim=1)
            best_s, best_i = cs2, torch.gather(best_i, 1, ci2)
            del V, sims
            torch.cuda.empty_cache()

        levels[p["name"]] = {
            "family": p["family"],
            "selectivity": p["selectivity"],
            "actual_selectivity": round(len(idx) / n_base, 6),
            "n_qualifying": int(len(idx)),
            "threshold": p.get("threshold"),
            "bucket_threshold": p.get("bucket_threshold"),
            "gt_ids": best_i.cpu().numpy().tolist(),
        }
        print(f"  {p['name']:<14} qualifying={len(idx):>10,} "
              f"({len(idx)/n_base:7.4%})  {time.time()-t0:.1f}s", flush=True)

    with open(OUT / f"gt_{args.tag}.json", "w") as f:
        json.dump({"tag": args.tag, "k": args.k, "nq": args.nq,
                   "n_base": int(n_base), "levels": levels}, f)
    print(f"wrote gt_{args.tag}.json  ({len(levels)} predicates)")


if __name__ == "__main__":
    main()
