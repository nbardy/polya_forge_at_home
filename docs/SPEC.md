# Pólya Forge specification

## Purpose

Forge turns one frozen problem goal into independently audited research
packets, a candidate problem-memory delta, and a later harness reflection.
It is an evidence producer, not a truth authority.

## Fixed graph

```text
PLAN → parallel (BUILD → VERIFY) → freeze packets → PLAN
  └ empty/budget → REMEMBER → REFLECT → close
```

`PLAN` emits exact briefs. First-wave briefs have no parents; every later brief
must cite packet IDs frozen by an earlier wave. Empty briefs stop research.

`BUILD` owns derivation or experiment. `VERIFY` is a separate call and records
`PASS`, `REPAIR`, `FAIL`, or `QUARANTINE`. Any verdict may inform a repair or
falsification child; only `PASS` packets may support terminal memory changes.

`REMEMBER` proposes evidence-linked changes to problem-local memory. It does
not edit canonical files. `REFLECT` runs afterward and may propose only
structural harness descendants and regression tests.

## Finiteness

Every goal fixes:

```clojure
{:fanout positive-integer
 :invocations positive-integer
 :wall-minutes positive-integer}
```

The engine caps all three. It reserves two terminal calls before research
fan-out. Invocation reservations are written before processes start, so
parallel calls cannot overspend the budget. There is no wave-count setting.

## Canonical artifacts

```text
run.edn                         immutable run manifest
input/                          frozen problem and goal inputs
calls/NNN-task/
  request.edn                   invocation reservation
  prompt.md
  events.jsonl                  raw Codex event stream
  result.json | error.edn       one terminal call outcome
packets/WNN-BRIEF.edn           one frozen verified branch
close.edn                       terminal memory/reflection pointers
```

Call directories preserve raw process evidence. A packet is the canonical
reusable research projection: it embeds the accepted brief, build, verifier,
artifact hashes, and distinct call numbers. It freezes as soon as its branch
verifies, so a successful sibling survives another sibling's failure. A failed
or interrupted call is never overwritten; resume allocates a later call.

The checked-out `engine/` tree and root rules are hashed as one harness. Git
commits and worktrees provide complete harness versions.

## Packets

A packet is defined by:

- its immutable brief and parent packet IDs;
- one builder result and any text artifacts found under `artifacts/`;
- one independent verifier result;
- artifact paths and hashes.

Its ID is derived from that content. Cross-run, forward, and missing parent
references are rejected.

## Export

Terminal runs produce two static bundles:

- research: goal inputs, packets, artifacts, and memory proposal;
- harness: process facts and post-memory structural proposals.

Each bundle declares one content hash over every path and byte in the bundle.
Inspection rejects symlinks, non-text or oversized files, and content-hash
mismatches. It never imports or executes bundle code.

Negative results are valid evidence and may be exported. Neither export nor a
model verdict admits mathematics.

## Non-goals

- Generic workflow or graph interpretation
- Runtime prompt/controller version mixing
- Automatic memory merge or harness activation
- Executing untrusted contribution code
- Model-vote truth
- Claiming repository acceptance satisfies external prize rules
