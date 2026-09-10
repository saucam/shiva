#!/usr/bin/env python3
"""Assemble LAION shards into a benchmark corpus.

Reads img_emb_{i}.npy (fp16, N x 512) + metadata_{i}.parquet from /data,
writes a contiguous corpus + attribute table to NVMe.

Adds a synthetic uniform `bucket` column in [0, 1000) so predicates can hit an
exact target selectivity. Real attributes (nsfw, similarity, width, height) are
kept alongside for the correlated-attribute case, which is the hard one.
"""
import argparse
import json
import time
from pathlib import Path

import numpy as np
import pyarrow.parquet as pq

RAW = Path("/data/shiva-bench/raw")
OUT = Path("/home/ydatta/shiva-work")
DIM = 512


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--shards", type=int, default=10)
    ap.add_argument("--tag", default="10m")
    args = ap.parse_args()

    OUT.mkdir(parents=True, exist_ok=True)
    t0 = time.time()

    vec_parts, attr_parts, total = [], [], 0
    for i in range(args.shards):
        npy = RAW / f"img_emb_{i}.npy"
        pqt = RAW / f"metadata_{i}.parquet"
        if not npy.exists() or not pqt.exists():
            raise SystemExit(f"missing shard {i}: {npy.exists()=} {pqt.exists()=}")

        v = np.load(npy, mmap_mode="r")
        tbl = pq.read_table(pqt)
        n = min(len(v), tbl.num_rows)
        vec_parts.append(np.asarray(v[:n], dtype=np.float16))

        cols = {c: tbl.column(c).to_numpy(zero_copy_only=False)[:n] for c in tbl.column_names}
        attr_parts.append(cols)
        total += n
        print(f"  shard {i}: {n:,} vectors  cols={list(tbl.column_names)}", flush=True)

    vecs = np.concatenate(vec_parts, axis=0)
    del vec_parts
    print(f"corpus: {vecs.shape} {vecs.dtype}  ({vecs.nbytes/2**30:.1f} GiB)")

    # normalise: CLIP embeddings are used with cosine; store unit-norm so cosine == dot
    v32 = vecs.astype(np.float32)
    norms = np.linalg.norm(v32, axis=1, keepdims=True)
    norms[norms == 0] = 1.0
    v32 /= norms
    np.save(OUT / f"vectors_{args.tag}.npy", v32.astype(np.float16))
    del v32

    # attributes
    keys = set.intersection(*[set(p.keys()) for p in attr_parts])
    attrs = {}
    for k in sorted(keys):
        try:
            attrs[k] = np.concatenate([p[k] for p in attr_parts])
        except Exception:
            pass

    rng = np.random.default_rng(1234)
    attrs["bucket"] = rng.integers(0, 1000, size=total, dtype=np.int32)

    # width/height are all 256 post-resize; original_* carry the real variance
    keep = {}
    for k, v in attrs.items():
        kl = k.lower()
        if k == "bucket":
            keep[k] = v
        elif kl == "similarity":
            # LAION's similarity column carries NaNs (~0.15%) and out-of-range
            # junk (observed max 2336 for what should be a cosine). A single NaN
            # makes np.quantile return NaN, which silently empties every
            # predicate built from it. Map anything invalid to -1 so it never
            # qualifies -- and so numpy and Qdrant agree on which rows those are.
            s = v.astype(np.float32)
            bad = ~np.isfinite(s) | (s < 0.0) | (s > 1.0)
            s[bad] = -1.0
            print(f"  similarity: {int(bad.sum()):,} invalid -> -1.0")
            keep["similarity"] = s
        elif kl in ("original_width", "original_height"):
            keep[kl] = np.nan_to_num(v.astype(np.float64), nan=0).astype(np.int32)
        elif kl in ("nsfw", "license"):
            # store as categorical codes; raw numpy unicode arrays size to the
            # longest value present (one outlier forces <U1574 -> 63 GiB at 10M)
            s = np.asarray([str(x) for x in v], dtype=object)
            cats, codes = np.unique(s, return_inverse=True)
            if len(cats) > 256:
                top = set(cats[np.argsort(-np.bincount(codes))[:255]])
                s = np.array([x if x in top else "__other__" for x in s], dtype=object)
                cats, codes = np.unique(s, return_inverse=True)
            keep[kl] = codes.astype(np.int16)
            keep[f"{kl}__categories"] = cats.astype("U64")

    np.savez(OUT / f"attrs_{args.tag}.npz", **keep)

    meta = {
        "n": int(total), "dim": DIM, "shards": args.shards,
        "attr_columns": {k: str(v.dtype) for k, v in keep.items()},
        "elapsed_s": round(time.time() - t0, 1),
    }
    (OUT / f"corpus_{args.tag}.json").write_text(json.dumps(meta, indent=2))
    print(json.dumps(meta, indent=2))


if __name__ == "__main__":
    main()
