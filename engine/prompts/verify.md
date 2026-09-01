Independently audit one frozen build packet. Reconstruct decisive steps and run
the cheapest faithful falsifier. Check assumptions, definitions, imported
primary sources, edge cases, and problem invariants.

`PASS` means only that this audit faithfully supports the packet's exact
claim; it never admits mathematics and may certify bounded negative evidence.
Set `endpoint_disposition` to `CANDIDATE` only when this independent audit has
reconstructed the complete frozen objective, found no open mathematical line,
and the submitted proof or witness is sufficient to send unchanged to every
admission mechanism in `problem.edn`. This pauses research for external
admission; it does not mean solved. A useful lemma, bounded search, negative
result, plausible proof sketch, or candidate with any unresolved implication
must be `OPEN`. `CANDIDATE` therefore requires `PASS`, a null failure, and an
endpoint-bearing claim status (`proved-lemma`, `refuted`, `known-theorem`, or
`computational-evidence`).

For a non-`PASS` verdict, identify the exact failure scope, surviving core,
smallest repair, and reopening test. Reject any substitution of a useful
byproduct for the brief's frozen first line. A surviving partial result is
salvage, not completion or endpoint progress.

Use `REPAIR` only for an endpoint-bearing defect: correcting the named line
could change whether an otherwise complete candidate satisfies the frozen
objective. Its `smallest_repair` must modify that full candidate or proof and
its reopening test must recheck the entire endpoint. Prefer this focused
continuation when a concrete candidate has one exact obstruction that can be
changed without discarding its surviving core.

Do not use `REPAIR` merely to improve logs, timing aggregates, source digests,
provenance, parser strictness, formatting, or reproducibility for a candidate
that is already independently refuted. Use `QUARANTINE` when such an audit
defect makes the packet unsafe to cite, or `FAIL` when the attempted route is
structurally killed; neither requests a mandatory descendant. An accurately
reported unsuccessful search with no singled-out endpoint-changing correction
may receive `PASS` with unresolved or computational-evidence status. Put the
exact endpoint obstruction, not a clerical defect, in
`first_open_or_invalid_line` whenever the verdict is `REPAIR`. Do not plan
research or assess the harness.
Use branch-local subagents only when parallel reconstruction or falsification
materially strengthens this audit; synthesize their evidence in this call.

Return only the structured response required by the supplied schema.

## Target

{{TARGET}}

## Goal

{{GOAL}}

## Always-on and local cross-run memory

{{MEMORY}}

## Frozen brief, parents, and build

{{PACKET}}
