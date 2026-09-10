#!/usr/bin/env python3
"""Predicate families for the filtered-ANN sweep.

The point of this module is to hold *selectivity constant* while varying how
strongly the attribute correlates with position in embedding space. That is the
comparison that matters: if recall degrades for `sim` but not `bucket` at the
same selectivity, the cause is correlation, not filter strength.

  bucket  -- uniform random int in [0,1000). Zero correlation with the vector.
             The easy case, and what most published benchmarks actually measure.
  sim     -- threshold on CLIP image/caption cosine similarity. Derived FROM the
             embedding, so qualifying points cluster in embedding space.
  conj    -- bucket AND sim. Two mid-selectivity predicates whose conjunction has
             no dedicated payload block, so filterable HNSW has no links for it.
"""
import numpy as np
from qdrant_client import models

LEVELS = [0.001, 0.005, 0.01, 0.05, 0.10, 0.25, 0.50, 1.0]


def _quantile_threshold(values, sel):
    """Upper threshold on `values` selecting the top `sel` fraction.

    Quantile is taken over valid entries only. prep.py maps invalid similarity
    to -1.0; including those would drag the threshold down and inflate
    selectivity. nanquantile is belt-and-braces in case a NaN slips through --
    plain np.quantile returns NaN from a single NaN and silently empties the
    predicate, which is exactly the bug this guards.
    """
    valid = values[values >= 0.0]
    if valid.size == 0:
        return float("inf")
    return float(np.nanquantile(valid, 1.0 - sel))


def build(attrs, n_base, families=("bucket", "sim", "conj")):
    """Return [{name, family, selectivity, mask, qfilter}] for every level."""
    bucket = attrs["bucket"][:n_base]
    sim = attrs["similarity"][:n_base].astype(np.float32)
    out = []

    for sel in LEVELS:
        if "bucket" in families:
            thr = int(round(sel * 1000))
            if thr >= 1000:
                mask = np.ones(n_base, dtype=bool)
                qf = None
            else:
                mask = bucket < thr
                qf = models.Filter(must=[models.FieldCondition(
                    key="bucket", range=models.Range(lt=thr))])
            out.append(dict(name=f"bucket@{sel}", family="bucket",
                            selectivity=sel, mask=mask, qfilter=qf))

        if "sim" in families and sel < 1.0:
            t = _quantile_threshold(sim, sel)
            mask = sim >= t
            qf = models.Filter(must=[models.FieldCondition(
                key="similarity", range=models.Range(gte=t))])
            out.append(dict(name=f"sim@{sel}", family="sim",
                            selectivity=sel, mask=mask, qfilter=qf,
                            threshold=t))

        if "conj" in families and sel < 1.0:
            # split the target selectivity across two independent-ish predicates
            per = np.sqrt(sel)
            bthr = int(round(per * 1000))
            t = _quantile_threshold(sim, per)
            mask = (bucket < bthr) & (sim >= t)
            qf = models.Filter(must=[
                models.FieldCondition(key="bucket", range=models.Range(lt=bthr)),
                models.FieldCondition(key="similarity", range=models.Range(gte=t)),
            ])
            out.append(dict(name=f"conj@{sel}", family="conj",
                            selectivity=sel, mask=mask, qfilter=qf,
                            threshold=t, bucket_threshold=bthr))
    return out
