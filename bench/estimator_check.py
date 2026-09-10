#!/usr/bin/env python3
"""Does Qdrant's cardinality estimate pick the right plan?

Qdrant chooses a search plan in lib/segment/src/index/hnsw_index/hnsw/read_view/
dispatch.rs with a three-branch heuristic, not a cost model:

    if query_cardinality.max < full_scan_threshold  -> exact plain scan
    if query_cardinality.min > full_scan_threshold  -> filtered HNSW graph
    else                                            -> random sampling decides

Both branches key off an *estimate*, never the true count. If the estimate is
loose for one attribute type but tight for another, two predicates matching the
same number of points can land on different plans and therefore different recall.

This script compares estimated count (exact=False) against true count
(exact=True) per predicate, and flags where the estimate straddles the threshold
-- the band where the plan is decided by a random sample.
"""
import argparse
import json
import sys
from pathlib import Path

import numpy as np
from qdrant_client import QdrantClient

sys.path.insert(0, str(Path(__file__).parent))
import predicates  # noqa: E402

OUT = Path("/home/ydatta/shiva-work")
COLL = "laion_bench"
DEFAULT_FULL_SCAN_THRESHOLD = 10_000   # types.rs:2356


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", default="10m")
    ap.add_argument("--url", default="http://localhost:6333")
    ap.add_argument("--threshold", type=int, default=DEFAULT_FULL_SCAN_THRESHOLD)
    args = ap.parse_args()

    gt = json.load(open(OUT / f"gt_{args.tag}.json"))
    attrs = np.load(OUT / f"attrs_{args.tag}.npz", allow_pickle=True)
    preds = {p["name"]: p for p in predicates.build(attrs, gt["n_base"])}
    c = QdrantClient(url=args.url, timeout=600, prefer_grpc=True)

    total = c.count(COLL, exact=True).count
    if total != gt["n_base"]:
        print(f"!! collection holds {total:,} points but ground truth was computed "
              f"on {gt['n_base']:,}. Recall numbers would be meaningless. "
              f"Is ingest still running?")
        sys.exit(1)
    print(f"collection matches ground truth base: {total:,} points\n")

    print(f"{'predicate':>14}{'true':>12}{'estimated':>12}{'est/true':>10}"
          f"{'plan':>16}  note")
    rows, mismatches = [], 0
    for name, spec in gt["levels"].items():
        p = preds.get(name)
        if p is None or p["qfilter"] is None:
            continue
        true_n = c.count(COLL, count_filter=p["qfilter"], exact=True).count
        est_n = c.count(COLL, count_filter=p["qfilter"], exact=False).count
        ratio = est_n / true_n if true_n else float("nan")

        # Qdrant's exact count should match the mask numpy used, or the two
        # systems disagree about which points qualify. A handful of points can
        # differ legitimately: the float32 `similarity` threshold rounds
        # differently on each side, so rows sitting exactly on the boundary can
        # fall either way. That is immaterial to recall@10; only flag drift big
        # enough to actually move the metric.
        delta = abs(true_n - spec["n_qualifying"])
        if delta and delta / max(spec["n_qualifying"], 1) > 0.001:
            mismatches += 1
            print(f"!! {name}: qdrant counts {true_n:,} vs mask {spec['n_qualifying']:,} "
                  f"({delta:,} points, {delta/spec['n_qualifying']:.3%}) -- material mismatch")
        elif delta:
            print(f"   {name}: boundary rounding, {delta} point(s) differ -- immaterial")

        # We only see the point estimate over gRPC, not the [min,max] interval the
        # planner uses, so infer which branch is plausible from where it sits.
        if est_n < args.threshold:
            plan, note = "exact (likely)", ""
        elif est_n > args.threshold * 3:
            plan, note = "graph (likely)", ""
        else:
            plan, note = "SAMPLED", "near threshold -> plan decided by random sample"
        if abs(ratio - 1.0) > 0.25:
            note = (note + "; " if note else "") + f"estimate off by {(ratio-1)*100:+.0f}%"

        print(f"{name:>14}{true_n:>12,}{est_n:>12,}{ratio:>10.3f}{plan:>16}  {note}")
        rows.append({"predicate": name, "family": spec["family"],
                     "selectivity": spec["selectivity"], "true": true_n,
                     "estimated": est_n, "ratio": round(ratio, 4),
                     "plan_guess": plan, "note": note})

    (OUT / "results").mkdir(exist_ok=True)
    with open(OUT / "results" / f"estimator_{args.tag}.json", "w") as f:
        json.dump({"tag": args.tag, "threshold": args.threshold,
                   "predicate_mismatches": mismatches, "rows": rows}, f, indent=2)
    print(f"\nwrote results/estimator_{args.tag}.json")
    if mismatches:
        print(f"WARNING: {mismatches} predicate(s) disagree between Qdrant and the "
              f"ground-truth mask; recall for those cells is not trustworthy.")
    else:
        print("all predicates agree between Qdrant and the ground-truth mask.")


if __name__ == "__main__":
    main()
