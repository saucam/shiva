#!/usr/bin/env python3
"""The Gate 0 figure: recall and throughput vs selectivity, by predicate family.

Left panel is the one that decides things -- recall@10 against selectivity with
one line per correlation regime. A flat uncorrelated line above a sagging
correlated/conjunction line is the effect; three flat lines mean no wedge.
"""
import argparse
import json
from pathlib import Path

import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt  # noqa: E402

OUT = Path("/home/ydatta/shiva-work")
FAMILIES = ["bucket", "sim", "conj"]
LABEL = {"bucket": "uncorrelated (bucket)",
         "sim": "correlated (similarity)",
         "conj": "conjunction (bucket AND sim)"}
COLOR = {"bucket": "#2E7D32", "sim": "#E65100", "conj": "#C62828"}


def series(rows, family, ef, acorn, field):
    pts = sorted([(r["actual_selectivity"], r[field]) for r in rows
                  if r["family"] == family and r["ef"] == ef and r["acorn"] == acorn])
    return [p[0] for p in pts], [p[1] for p in pts]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", default="10m")
    ap.add_argument("--ef", type=int, default=64)
    args = ap.parse_args()

    data = json.load(open(OUT / "results" / f"qdrant_{args.tag}.json"))
    rows, n = data["rows"], data.get("nq")

    fig, axes = plt.subplots(1, 3, figsize=(17, 5))

    ax = axes[0]
    for f in FAMILIES:
        x, y = series(rows, f, args.ef, False, "recall@10")
        if x:
            ax.plot(x, y, "o-", color=COLOR[f], label=LABEL[f], lw=2, ms=6)
        x, y = series(rows, f, args.ef, True, "recall@10")
        if x:
            ax.plot(x, y, "s--", color=COLOR[f], alpha=0.45, lw=1.2, ms=4)
    ax.axhline(0.95, color="k", ls=":", lw=1, label="recall floor 0.95")
    ax.set_xscale("log"); ax.set_xlabel("predicate selectivity (fraction passing)")
    ax.set_ylabel("recall@10 vs exact"); ax.set_title(f"Recall (ef={args.ef})\nsolid = ACORN off, dashed = ACORN on")
    ax.legend(fontsize=8); ax.grid(alpha=0.3)

    ax = axes[1]
    for f in FAMILIES:
        x, y = series(rows, f, args.ef, False, "qps_batched")
        if x:
            ax.plot(x, y, "o-", color=COLOR[f], label=LABEL[f], lw=2, ms=6)
        x, y = series(rows, f, args.ef, True, "qps_batched")
        if x:
            ax.plot(x, y, "s--", color=COLOR[f], alpha=0.45, lw=1.2, ms=4)
    ax.set_xscale("log"); ax.set_yscale("log")
    ax.set_xlabel("predicate selectivity"); ax.set_ylabel("QPS (batched, median of 3)")
    ax.set_title(f"Throughput (ef={args.ef})"); ax.legend(fontsize=8); ax.grid(alpha=0.3)

    ax = axes[2]
    for f in FAMILIES:
        off = {r["actual_selectivity"]: r for r in rows
               if r["family"] == f and r["ef"] == args.ef and not r["acorn"]}
        on = {r["actual_selectivity"]: r for r in rows
              if r["family"] == f and r["ef"] == args.ef and r["acorn"]}
        keys = sorted(off.keys() & on.keys())
        if keys:
            ax.plot(keys, [off[s]["qps_batched"] / on[s]["qps_batched"] for s in keys],
                    "o-", color=COLOR[f], label=LABEL[f], lw=2, ms=6)
    ax.axhline(1.0, color="k", ls=":", lw=1)
    ax.set_xscale("log"); ax.set_xlabel("predicate selectivity")
    ax.set_ylabel("QPS(ACORN off) / QPS(ACORN on)")
    ax.set_title("ACORN throughput tax\n(higher = ACORN costs more)")
    ax.legend(fontsize=8); ax.grid(alpha=0.3)

    fig.suptitle(f"Qdrant 1.19.1 filtered ANN — LAION {args.tag}, "
                 f"recall@10 vs exact GPU ground truth, nq={n}", fontsize=12)
    fig.tight_layout()
    p = OUT / "results" / f"gate0_{args.tag}.png"
    fig.savefig(p, dpi=140)
    print(f"wrote {p}")


if __name__ == "__main__":
    main()
