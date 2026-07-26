# Pólya Forge specification

## Purpose

Forge directs bounded attacks at one goal's frozen first missing implication,
then preserves independently audited evidence, fallback memory, and a later
harness reflection. It is an evidence producer, not a truth authority.

## Fixed graph

```text
PLAN → parallel (BUILD → VERIFY) → freeze packets → PLAN
  └ empty/budget → REMEMBER → REFLECT → close
                                      └ launcher tests candidate → next run
```

`PLAN` emits exact briefs. Every brief copies the goal's objective, endpoint
edge, and first open line verbatim; fan-out varies only the direct strategy.
First-wave briefs have no parents; every later brief must cite packet IDs
frozen by an earlier wave. Empty briefs stop research. Reusable side work is
never a brief objective.

`BUILD` owns derivation or experiment. `VERIFY` is a separate call and records
`PASS`, `REPAIR`, `FAIL`, or `QUARANTINE`. Any verdict may inform a repair or
falsification child; only `PASS` packets may support terminal memory changes.

`REMEMBER` proposes evidence-linked fallback salvage to problem-local memory.
Memory cannot satisfy the goal, justify another wave, or score harness quality.
`REFLECT` runs afterward and may make one evidence-backed file change in an
inert candidate engine. Neither stage can alter the generating run or activate
its own output.

## Between-run evolution

`.forge/CURRENT.edn` names the active version under
`.forge/versions/vNNNN/engine`. The launcher freezes that selection for a run,
and reflection receives the path of an inert candidate copied into its own call
directory, plus the prior activation history, run record, and candidate memory.

Reflection returns a concise assessment and either no mutation or one mutation
record containing:

```text
changed_file, hypothesis, evidence_refs, expected_benefit,
regression_risk, benchmark_test
```

`evidence_refs` are durable audit locators. The launcher validates and records
their shape, but does not claim to understand their mathematical relevance.
`changed_file` is relative to the candidate engine root. The launcher rejects
path escapes, symlinks, malformed reflection pointers, and any diff other than
the one declared file. After the run closes, it reads the structured reflection
result directly, validates the call-local candidate, and runs its fixed smoke
suite. `benchmark_test` describes the frozen comparison that could
falsify the hypothesis; it is data and is never executed as a model-supplied
command. The comparison must measure verified movement on the same frozen
endpoint line; artifact count, memory volume, and future usefulness score zero.
Only then may the launcher create the next immutable version, append an
activation record under `.forge/activations/`, and atomically advance
`.forge/CURRENT.edn`. The candidate cannot modify the generating run or
activate itself.

If validation, a smoke test, an invariant, or the launched process fails, the
launcher records rejection and keeps or restores the prior known-good version
before another run. Evolution therefore occurs only at run boundaries.

The immutable launcher executes active and candidate engines through the
repository's `polya-forge-engine` Codex permission profile. That profile grants
the evolvable process read-only repository access plus dynamically scoped
writes to only its assigned run, same-problem memory, exports when requested,
and system temp. It does not grant writes to prior runs, the launcher, source,
immutable versions, active pointer, or receipts.

Problem-local cross-run memory lives at
`.forge/memory/<problem-id>/INDEX.edn`. It is candidate research context, not
mathematical admission; the problem pack's admission gate remains authoritative.
The capped index points into full run trees. Immutable packets, call logs,
prompts, failures, and artifacts are the searchable long-form layer; the
problem pack's curated `memory/KEY_LEARNINGS.md` is the always-on layer.
Multi-round campaign manifests under `.forge/campaigns/` point to each run and
its executing and successor version, so interruption does not lose the round
count or lineage. The next run ID is journaled before launch. A run abandoned
after probation rollback remains in `failed-runs`; the same campaign round then
restarts on the restored parent rather than resuming the rejected engine.

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
  candidate/engine/             inert copy, only for reflection
packets/WNN-BRIEF.edn           one frozen verified branch
close.edn                       terminal memory/reflection result pointers
```

Call directories preserve raw process evidence. A packet is the canonical
research evidence record: it embeds the accepted brief, build, verifier,
artifact hashes, and distinct call numbers. Reusability does not make it
progress. It freezes as soon as its branch verifies, so a successful sibling
survives another sibling's failure. A failed or interrupted call is never
overwritten; resume allocates a later call.

The active `engine/` tree is content-hashed in the run manifest. Root and
problem rules freeze separately with the run inputs. Git versions public
source; the local launcher stores immutable runtime engine versions.

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
- In-run memory merge or harness activation
- Executing untrusted contribution code
- Model-vote truth
- Claiming repository acceptance satisfies external prize rules
