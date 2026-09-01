# Pólya Forge specification

## Purpose

Forge directs bounded attacks at one goal's frozen first missing implication,
then preserves independently audited evidence, fallback memory, and a later
harness reflection. It is an evidence producer, not a truth authority.

## Fixed graph

```text
PLAN → parallel (durable BUILD → fresh VERIFY) → freeze packets → PLAN
  │                                  └ exact endpoint → candidate pause
  └ empty/budget/candidate → REMEMBER → REFLECT → close
                              └ compatibility gate → probationary challenger
                                   └ matched blinded benchmark → confirm/rollback
```

`PLAN` emits exact briefs. Every brief copies the goal's objective, endpoint
edge, and first open line verbatim; fan-out varies only the direct strategy.
First-wave briefs have no parents; every later brief must cite packet IDs
frozen by an earlier wave. Empty briefs stop research. Reusable side work is
never a brief objective.

`BUILD` owns derivation or experiment. A fresh build starts a durable Codex
app-server thread. When a brief names exactly one `REPAIR` lineage, its new
call resumes that builder thread with the complete rejected candidate,
`smallest_repair`, and reopening test. The call still receives a new immutable
directory whose `cwd`, runtime workspace root, and only writable root are that
directory. Multiple named repair lineages fail closed instead of choosing one.

`VERIFY` is a separate, fresh ephemeral call and can never receive a builder
conversation ID. It records `PASS`, `REPAIR`, `FAIL`, or `QUARANTINE`. Any
verdict may inform a repair or falsification child; only `PASS` packets may
support terminal memory changes.
An unconsumed `REPAIR` packet keeps its exact endpoint repair live: the next
plan must cite it and retry the complete objective. Cross-run memory IDs are
evidence pointers, never current-run graph parents. A recoverable branch or
model-call failure closes the round through memory and reflection instead of
crashing the campaign, and one call cannot consume the entire round budget.

A verifier sets `endpoint_disposition` to `CANDIDATE` only for a `PASS` packet
that reconstructs the entire frozen objective and has no open mathematical
line. The controller immediately stops planning, performs the normal terminal
memory/reflection sequence, and writes an endpoint record with
`:admission :pending`. A campaign records status `:candidate` and refuses more
rounds. This is a quarantine boundary for external admission, not a solution
or an admission decision.

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
Only then may the launcher create the next immutable version and append a
probationary activation record under `.forge/activations/`. Compatibility does
not advance `.forge/CURRENT.edn`: the existing version remains the champion,
only one challenger may be open, and a probationary challenger cannot produce
another descendant. The candidate cannot modify the generating run or select
itself.

Selection is an explicit `bb benchmark <trusted-goal>...` operation. The
launcher runs champion and challenger on the same blinded solved endpoint
goal, hash, and budget, restoring the benchmark's problem-memory baseline
between arms. It derives `whole-endpoint?` from the candidate-pause record and
derives `independently-admitted?` with its own exhaustive predicate; no model
score or CLI-supplied result is trusted. The challenger becomes `CURRENT` only
if it produces strictly more independently admitted whole endpoints. A loss,
an equal score—including zero/zero—or a benchmark failure records rollback and
retains the champion.

If validation, a smoke test, an invariant, or the launched process fails, the
launcher records rejection and keeps or restores the prior known-good version.
Candidate creation and selection therefore occur only outside the generating
run. `bb adopt` remains a separate trusted-development operation: it is blocked
while a challenger is open and directly advances checked-in source only after
the compatibility gate.

The immutable launcher executes active and candidate engines through the
repository's `polya-forge-engine` Codex permission profile. That profile grants
the evolvable process read-only repository access plus dynamically scoped
writes to only its assigned run, same-problem memory, exports when requested,
and system temp. It does not grant writes to prior runs, the launcher, source,
immutable versions, active pointer, or receipts.

The public run input is one repository-relative goal path. Before evolvable
code starts, the launcher parses and hashes its exact bytes and records the run
ID, problem, engine pin, goal hash, and launcher hash in launcher-owned
`.forge/RUNS.edn`. Resume derives its writable problem-memory scope from this
record, never from the engine-written run manifest. The frozen goal and
manifest must still match the assignment.

Problem-local cross-run memory lives at
`.forge/memory/<problem-id>/INDEX.edn`. It is candidate research context, not
mathematical admission; the problem pack's admission gate remains authoritative.
The capped index points into full run trees. Immutable packets, call logs,
prompts, failures, and artifacts are the searchable long-form layer; the
problem pack's curated `memory/KEY_LEARNINGS.md` is the always-on layer.
Multi-round campaign manifests under `.forge/campaigns/` point to each run and
its executing and successor version, so interruption does not lose the round
count or lineage. The next run ID is journaled before launch. A run abandoned
after a rollback remains in `failed-runs`; the same campaign round then
restarts on the restored champion rather than resuming the rejected engine.
Campaign format 2 also pins the goal hash and declared problem. Earlier local
format-1 campaign manifests fail closed rather than being guessed into the new
authority model. Started rounds resume from their frozen input and launcher
assignment; unstarted rounds require the live goal to retain its pinned hash.

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
  thread.edn                    emitted conversation ID, when present
  result.json | error.edn       one terminal call outcome
  candidate/engine/             inert copy, only for reflection
packets/WNN-BRIEF.edn           one frozen verified branch
close.edn                       terminal memory/reflection result pointers
```

Call directories preserve raw process evidence. A packet is the canonical
research evidence record: it embeds the accepted brief, build, verifier,
builder conversation ID when present, artifact hashes, and distinct call
numbers. Reusability does not make it progress. It freezes as soon as its
branch verifies, so a successful sibling survives another sibling's failure.
A failed or interrupted call is never overwritten; resume allocates a later
call. Continuing a repair thread does not reuse or mutate its ancestor's call
directory.

The active `engine/` tree is content-hashed in the run manifest. Root and
problem rules freeze separately with the run inputs. Git versions public
source; the local launcher stores immutable runtime engine versions.

## Packets

A packet is defined by:

- its immutable brief and parent packet IDs;
- one builder result and any text artifacts found under `artifacts/`;
- one independent verifier result;
- the durable builder conversation ID when emitted;
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

## Public runs

The fixed launcher may publish a terminal research export under
`runs/<problem-id>/<run-uuid>/`. It validates bounded text-only content,
required research paths, run/problem identity, and the bundle content hash
before an atomic copy. Harness bundles, raw events, interrupted calls, local
controller state, and reflection process records remain in `.forge/`.

Publication is distribution, not mathematical admission.

## Non-goals

- Generic workflow or graph interpretation
- Runtime prompt/controller version mixing
- In-run memory merge or harness activation
- Executing untrusted contribution code
- Model-vote truth
- Claiming repository acceptance satisfies external prize rules
