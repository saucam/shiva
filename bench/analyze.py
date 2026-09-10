#!/usr/bin/env python3
"""Turn a sweep result file into the tables that decide Gate 0.

Three questions:
  1. Does recall degrade with selectivity, and does correlation make it worse?
     (compare families at identical selectivity)
  2. What does ACORN cost to recover that recall?
  3. What is throughput at a fixed recall floor -- the headline metric?
"""
import argparse
import json
from collections import defaultdict
from pathlib import Path

OUT = Path("/home/ydatta/shiva-work")
FAMILIES = ["bucket", "sim", "conj"]
LABEL = {"bucket": "uncorrelated", "sim": "correlated", "conj": "conjunction"}


def load(tag):
    return json.load(open(OUT / "results" / f"qdrant_{tag}.json"))["rows"]


def table_recall(rows, ef, acorn):
    sel_set = sorted({r["selectivity"] for r in rows})
    idx = {(r["family"], r["selectivity"]): r for r in rows
           if r["ef"] == ef and r["acorn"] == acorn}
    print(f"\n## recall@10 by predicate family   (ef={ef}, acorn={acorn})")
    print(f"{'selectivity':>12} " + "".join(f"{LABEL[f]:>15}" for f in FAMILIES)
          + f"{'corr penalty':>15}{'conj penalty':>15}")
    for s in sel_set:
        cells, base = [], idx.get(("bucket", s))
        for f in FAMILIES:
            r = idx.get((f, s))
            cells.append(f"{r['recall@10']:.4f}" if r else "-")
        pen = []
        for f in ("sim", "conj"):
            r = idx.get((f, s))
            pen.append(f"{(r['recall@10']-base['recall@10'])*100:+.2f}pp"
                       if r and base else "-")
        print(f"{s:>12.3f} " + "".join(f"{c:>15}" for c in cells)
              + "".join(f"{p:>15}" for p in pen))


def table_acorn_tax(rows, ef):
    on = {(r["family"], r["selectivity"]): r for r in rows
          if r["ef"] == ef and r["acorn"]}
    off = {(r["family"], r["selectivity"]): r for r in rows
           if r["ef"] == ef and not r["acorn"]}
    print(f"\n## ACORN tax   (ef={ef})   -- recall gained vs throughput/latency paid")
    print(f"{'predicate':>14}{'recall off':>12}{'recall on':>12}{'d recall':>11}"
          f"{'qps off':>10}{'qps on':>10}{'qps cost':>10}{'p50 cost':>10}")
    for key in sorted(on.keys() & off.keys(), key=lambda k: (k[0], k[1])):
        a, b = off[key], on[key]
        qc = a["qps_batched"] / b["qps_batched"] if b["qps_batched"] else float("nan")
        pc = b["p50_ms"] / a["p50_ms"] if a["p50_ms"] else float("nan")
        print(f"{key[0]+'@'+str(key[1]):>14}{a['recall@10']:>12.4f}{b['recall@10']:>12.4f}"
              f"{(b['recall@10']-a['recall@10'])*100:>+10.2f}pp"
              f"{a['qps_batched']:>10.0f}{b['qps_batched']:>10.0f}"
              f"{qc:>9.2f}x{pc:>9.2f}x")


def table_qps_at_recall(rows, floor):
    """Best throughput among configs meeting the recall floor. The headline."""
    best = defaultdict(lambda: None)
    for r in rows:
        if r["recall@10"] < floor:
            continue
        key = (r["family"], r["selectivity"])
        if best[key] is None or r["qps_batched"] > best[key]["qps_batched"]:
            best[key] = r
    print(f"\n## max QPS at recall@10 >= {floor}   (best config per cell)")
    print(f"{'selectivity':>12} " + "".join(f"{LABEL[f]:>26}" for f in FAMILIES))
    for s in sorted({r["selectivity"] for r in rows}):
        cells = []
        for f in FAMILIES:
            r = best[(f, s)]
            cells.append(f"{r['qps_batched']:>8.0f} (ef{r['ef']}"
                         f"{',acorn' if r['acorn'] else ''})" if r else "UNREACHABLE")
        print(f"{s:>12.3f} " + "".join(f"{c:>26}" for c in cells))


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--tag", default="10m")
    ap.add_argument("--ef", type=int, default=64)
    ap.add_argument("--floor", type=float, default=0.95)
    a = ap.parse_args()
    rows = load(a.tag)
    efs = sorted({r["ef"] for r in rows})
    print(f"# {a.tag}: {len(rows)} rows, ef values {efs}")
    for ef in efs:
        table_recall(rows, ef, acorn=False)
    table_acorn_tax(rows, a.ef)
    table_qps_at_recall(rows, a.floor)
