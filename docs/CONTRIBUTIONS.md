# Contribution bundles

`bb export <run-id>` produces two independent bundles.

## Research bundle

Contains the frozen goal and problem inputs, immutable branch packets, their
text evidence, and the terminal fallback-memory proposal. Export preserves
salvage; it does not turn salvage into claimed endpoint progress.

It must let a reviewer identify:

- the exact objective and first open line;
- every claim status, assumption, failed step, and remaining check;
- each brief's parent packet IDs;
- the independent verifier evidence;
- what future work the proposed memory change would alter.

## Harness bundle

Contains run budgets, process outcomes, and the harness-only reflection emitted
after memory. It contains no mathematical progress receipt.

Research results and engine changes must use separate pull requests.

To publish and open a research PR from a clean checkout:

```bash
bb propose <run-uuid>
```

This validates the terminal research export, copies only the sanitized bundle
to `runs/<problem>/<uuid>/`, commits that path on a proposal branch, pushes it,
and opens a GitHub PR. Proposal and research-lead decision rules live in
[`PROPOSALS.md`](PROPOSALS.md).

## Static inspection

Each bundle has one `bundle.json` declaring its kind, run ID, engine hash, and
one content hash over its complete file tree. `bb inspect` rejects:

- absolute, escaping, or symlinked paths;
- non-text, oversized, symlinked, or content-hash-mismatched files.

Inspection never executes bundle content.

`PASS` means an internal verifier call passed. An endpoint `CANDIDATE` means
the controller paused with admission pending; publish or review it as a frozen
candidate, never as a solved claim. A claim becomes admitted only through the
external mechanism declared by its problem pack. Repository merge does not
satisfy any prize body's rules.
