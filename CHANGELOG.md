# Changelog

## Unreleased

- Make nested model calls ignore ambient Codex exec-policy rule files. Each
  call already receives the run-frozen repository and problem rules through
  its local `AGENTS.md`; attempting to read `~/.codex/rules` failed closed
  inside the launcher sandbox on Codex CLI 0.145.

- Added UUID run identifiers and fixed-launcher publication of sanitized,
  content-hashed research bundles under `runs/<problem>/<uuid>/`.
- Made the goal path the generic public input and moved controller regression
  data out of the Poincaré pack into one test-only fixture.
- Added launcher-owned run authority and campaign goal hashes. Legacy local
  format-1 campaign manifests now fail closed on resume.
- Made the official endpoint line the only research objective and harness
  quality measure; partial byproducts are fallback salvage only.
- Added structural plan guards and fixed candidate regressions against
  objective, endpoint-edge, or first-open-line drift.
- Reduced the controller to one fixed packet-linked research loop.
- Replaced runtime harness-version metadata with whole-harness Git versions.
- Removed the model progress gate, duplicate receipts, wave limits, duplicate
  artifacts, and subjective export blocking.
- Added parent-linked successor briefs and content-derived packet IDs.
- Made run state append-only: one manifest, immutable calls, independently
  frozen branch packets, and one close record.
- Added a real mid-wave interruption/resume fixture.
- Split static exports into research and harness bundles.
- Kept GitHub Actions disabled.

## v0.1.0 — 2026-07-23

- Created the standalone local-first repository and seven Millennium problem
  packs.
- Added bounded Codex research, independent verification, resume, static
  bundles, and non-executing inspection.
