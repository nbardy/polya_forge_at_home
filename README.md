# Pólya Forge at Home

Pólya Forge is a self-improving autonomous swarm for solving open math
problems with AI through bounded, auditable research campaigns. It is designed
for researchers who want model-assisted exploration without treating a
plausible completion—or model consensus—as a proof.

The central idea is simple: freeze one precise endpoint, have independent
workers attack that same missing implication, preserve every failure, and
stop when the finite evidence budget is exhausted or a complete candidate is
ready for external admission. Pólya Forge produces research evidence, not
truth by consensus.

What you get:

- durable, parallel build attempts with fresh independent verification;
- immutable prompts, packets, hashes, failures, and run history;
- problem-local memory that preserves useful lessons without changing the
  research objective;
- fixed launcher gates, blinded regression benchmarks, and rollback for
  evolved harnesses; and
- sanitized, content-hashed research bundles suitable for sharing.

The repository includes problem packs for the Clay Millennium Problems, a
Poincaré reference pack, Erdős targets, and source-withheld benchmarks for
testing the harness itself.

The core loop is:

```text
frozen problem + goal
        ↓
PLAN exact briefs citing audited parent packets
        ↓
parallel durable BUILD → fresh independent VERIFY
        │                         └ exact whole endpoint
        │                                      ↓
        │                         candidate pause for admission
        ↓
freeze packets ───────────────→ PLAN again
                                      ↓ empty or budget
                              problem MEMORY proposal
                                      ↓
                              HARNESS reflection
                                      ↓
                         inert one-file candidate
                                      ↓
                         compatibility gate
                                      ↓
                    probationary challenger (not active)
                                      ↓
             matched blinded benchmark → confirm or rollback
```

There is no model progress gate. Verification fixes the evidence; the next
planner either cites audited packets and continues or stops. Model calls,
parallel fan-out, and wall time are finite.

## Core invariants

- One run touches one problem pack and one active goal.
- Every brief preserves the goal's exact objective, endpoint edge, and first
  open line; fan-out changes strategy, not the target.
- Exact inputs and the active engine version are hashed before research.
- A fresh builder starts a durable Codex conversation. A repair descendant with
  one exact rejected lineage continues that same conversation, but writes a new
  immutable call directory and receives the verifier's complete defect record.
- Every verification is a fresh independent conversation; a verifier can never
  inherit the builder thread.
- Failed and interrupted calls are preserved; resume creates a new call.
- Every call keeps its exact prompt, raw Codex JSONL, and conversation ID when
  emitted; each run pins the immutable engine tree that produced them.
- Later briefs cite immutable parent packet IDs.
- Reusable byproducts and memory are fallback salvage, not objectives,
  completion, progress, or harness-quality signals.
- Problem-memory reconciliation happens after research.
- Harness reflection happens once, after memory, and cannot change its own run.
- A candidate changes at most one engine file and cannot activate itself.
- A complete independently passing endpoint packet pauses the run and campaign
  with admission still pending; it is not labeled solved.
- Compatibility, authority, and lifecycle checks can install a probationary
  challenger, but cannot make it active. The launcher promotes it only after a
  matched blinded tournament yields strictly more independently admitted whole
  endpoints than the champion; a tie or loss rolls it back.
- `PASS` is an internal audit verdict, never mathematical admission.

## Run it

Requirements are Babashka, Git, and Codex CLI 0.138 or newer. The fixed
launcher uses Codex's `polya-forge-engine` permission profile to run evolvable
engine code with write access only to its assigned run, same-problem memory,
requested export, and system-temp state. It fails closed if that sandbox
cannot be enforced.

```bash
bb check
bb test
bb adopt
bb run problems/navier-stokes/goals/my-goal.edn
bb campaign 10 problems/navier-stokes/goals/my-goal.edn
bb campaign-resume <campaign-id>
bb benchmark problems/blinded-endpoint-benchmark/goals/find-token.edn
bb publish <run-id>
```

The goal is the single public input. Its `:problem` field selects the matching
problem pack, memory, and sandbox scope. The launcher hashes its exact bytes
and records a write-protected run assignment before starting evolvable code.
`campaign` runs sequentially and atomically records round IDs and exact version
lineage under `.forge/campaigns/`. `campaign-resume` continues an interrupted
campaign, first resuming any interrupted round. Reflection may install one
compatibility-checked challenger after its generating round closes, but
`.forge/CURRENT.edn` remains pinned to the champion. Further reflected
descendants are rejected until that challenger is selected or rolled back.
Every campaign pins its initial goal hash and problem. A started round resumes
from frozen input; a new round starts only while the source goal still has that
exact hash.
The next run ID is persisted before launch. `bb benchmark` runs the champion
and the one open challenger on the same fixed blinded goal, exact goal hash,
and budget, restoring the benchmark memory baseline between arms. Only the
fixed launcher's exhaustive endpoint gate supplies the benchmark admission
bit. A benchmark error or non-winning challenger retains the champion and
records rollback evidence.
Inside a round, build→verify arms run in parallel and may use branch-local
subagents; planning, memory, reflection, and successive rounds stay sequential
because each consumes the frozen output before it.
`adopt` is the explicit trusted-development path: it runs the same fixed gate
before making checked-in `engine/` changes the next immutable local version.

Create goals from [`templates/goal.edn`](templates/goal.edn). A goal contains
the actual finite budget:

```clojure
{:budget {:fanout 3 :invocations 24 :wall-minutes 240}}
```

The engine-wide safety ceilings live in
[`engine/config.edn`](engine/config.edn). There is no separate wave limit:
invocations and wall time already make the loop finite.

If a process is interrupted:

```bash
bb resume <run-id>
```

If a verifier marks a complete endpoint `CANDIDATE`, the controller performs
terminal memory and reflection, records `:admission :pending` in `close.edn`,
and starts no further research wave. A containing campaign becomes
`:candidate` and cannot be resumed. Submit the frozen packet unchanged to the
admission mechanisms in `problem.edn`; neither `PASS` nor this pause is a solve.

When a run is terminal:

```bash
bb export <run-id>
bb inspect .forge/exports/<bundle>
```

Export creates separate research and harness bundles so their pull requests
cannot accidentally be conflated. Inspection checks text-only paths, hashes,
file sets, and symlinks without executing contributed content. Completed
negative and failed-route research remains exportable.

## Repository layout

```text
engine/
  forge.clj              thin command entrypoint
  forge/core.clj         research controller
  forge/bundle.clj       static export and inspection
  prompts/               five model-facing roles
  schemas/               five structured outputs
kernel/launcher.clj      fixed authority, version, and sandbox boundary
kernel/codex_app_server.clj
                          durable builder-thread protocol boundary
problems/<id>/            problem-local targets, goals, memory, and results
test/fixtures/            synthetic controller data, separate from mathematics
templates/goal.edn        bounded goal contract
.forge/runs/<id>/         ignored append-only local evidence
.forge/campaigns/         ignored resumable campaign journals
.forge/exports/<id>/      ignored static bundles
runs/<problem>/<uuid>/    public sanitized research bundles
```

Git versions the public source, while the launcher snapshots each active
engine. A descendant may change topology, roles, tools, search policy, or
allocation. Reflection receives a call-local copy plus the prior evolution
history, run record, and local memory candidate. It may edit one file and must
name the evidence, expected benefit, regression risk, and frozen endpoint-line
benchmark. A mutation that rewards partial-work volume or future usefulness is
a regression.

The launcher owns `.forge/CURRENT.edn`, immutable versions under
`.forge/versions/`, and append-only `.forge/activations/`. It runs fixed
compatibility checks after a candidate's generating run closes and installs at
most one challenger on probation without changing `CURRENT`. `bb benchmark`
then confirms a strict admitted-endpoint win or records rollback; an all-zero
or equal-score result retains the champion. The model's benchmark field is
descriptive, never an executable command. The trusted tournament goal and
admission predicate are launcher-owned inputs. Problem-local
`.forge/memory/<problem-id>/INDEX.edn` carries candidate context across runs
but never admits mathematics.

That index stays small: it keeps at most 32 recent evidence pointers and terse
behavior-changing deltas. Full derivations, failures, prompts, raw events, and
artifacts remain in the referenced `.forge/runs/<id>/` tree as searchable
long-form memory. The problem pack's curated `memory/KEY_LEARNINGS.md` is the
always-on layer.

New runs use UUID identifiers. `bb publish <run-id>` validates the terminal
research export in the fixed launcher, excludes raw process and harness data,
and atomically copies the content-hashed bundle to
`runs/<problem-id>/<run-uuid>/`. Repeating publication is idempotent only when
the existing public bytes match the validated export.

## Truth boundary

The repository begins with packs for the seven Clay Millennium Prize Problems;
six remain open and Poincaré is retained as a solved reference. Pack summaries
do not replace official problem statements.

Repository acceptance is not recognition by the Clay Mathematics Institute.
Mathematical admission requires the external mechanism declared by the problem
pack, normally formal checking or qualified independent human review.

The local runner is suitable for trusted research checkouts. It never executes
contributor-supplied bundle code during ordinary inspection.
