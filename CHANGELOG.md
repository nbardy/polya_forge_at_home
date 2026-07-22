# Changelog

## Unreleased — v0.2.0

- Added deterministic active-goal preflight before model invocation.
- Changed wall time from a per-call timeout to one global run deadline.
- Added bounded iterative research waves with manager-directed parallel
  fan-out, independent verification, and receipt-gated continuation.
- Reject zero-work initial launches and prevent export of runs without an
  independently verified frontier delta.
- Added a dedicated deterministic controller fixture and preserved v0.1 as an
  immutable engine version.

## v0.1.0 — 2026-07-23

- Created the standalone Pólya Forge at Home repository.
- Added a generic Babashka engine with validation, bounded Codex rounds,
  independent verification, stage-boundary resume, export, and static inspect.
- Added seven Millennium problem packs, with Poincaré marked solved/reference.
- Added public schemas, trust model, governance, contribution protocol,
  deterministic tests, and static GitHub validation.
- Specified swarm-based test-time compute, recursive harness optimization,
  multi-user claim leasing, long-horizon checkpoints, and asynchronous merge.
