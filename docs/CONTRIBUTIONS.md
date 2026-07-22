# Contribution bundle protocol

## Bundle purpose

A bundle is the smallest public, reviewable research unit. It must let a
reviewer answer:

- What exact question was attempted?
- What inputs and exclusions were used?
- What was derived, computed, falsified, or verified?
- What failed?
- Which agent or tool produced each artifact?
- What changed in the dependency graph?
- What independent review remains?

## Export allowlist

v0.1 exports only:

```text
bundle.json
goal.md
target.md
briefs/*.json
briefs/*.md
results/*/executor.json
results/*/verifier.json
memory/memory_delta.json
review/review.json
review/polya_receipt.json
published/manifest.edn
```

Raw event logs, stderr, full prompts, credentials, environment variables,
unlisted attempt files, generated binaries, and absolute local paths are not
exported.

## Validation

`bb inspect` performs non-executing checks:

- Required files and format version
- SHA-256 manifest integrity
- No symlinks
- No absolute paths or `..` traversal in the bundle manifest
- File-count and file-size limits
- Problem ID, goal hash, engine version, and run ID presence
- Separation of mathematical and engine-change proposals

Inspection establishes packaging integrity only. It does not validate a proof.

## Review pipeline

```text
pull request
  -> static bundle inspection
  -> provenance/privacy triage
  -> duplicate and scope check
  -> independent mathematical reproduction or audit
  -> problem-maintainer decision
  -> optional external admission gate
```

Research PRs should contain exactly one primary claim or one explicitly scoped
family of mechanically identical claims. Broad archives and raw chat dumps are
not reviewable contributions.

## Learning proposals

A bundle may propose a problem-local learning. Promotion requires verified,
non-obvious, behavior-changing evidence and a completed closeout review.
Engine-wide process learnings require evidence across problems or a precise
reason the lesson is domain-independent.

