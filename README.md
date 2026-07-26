# Pólya Forge at Home

Pólya Forge runs bounded, auditable research campaigns against exact
mathematical goals. It does not decide that a proof is true.

The core loop is:

```text
frozen problem + goal
        ↓
PLAN exact briefs citing audited parent packets
        ↓
parallel BUILD → independent VERIFY
        ↓
freeze packets ───────────────→ PLAN again
                                      ↓ empty or budget
                              problem MEMORY proposal
                                      ↓
                              HARNESS reflection
                                      ↓
                         inert one-file candidate
                                      ↓
                    launcher test → activate next run
```

There is no model progress gate. Verification fixes the evidence; the next
planner either cites audited packets and continues or stops. Model calls,
parallel fan-out, and wall time are finite.

## Core invariants

- One run touches one problem pack and one active goal.
- Every brief preserves the goal's exact objective, endpoint edge, and first
  open line; fan-out changes strategy, not the target.
- Exact inputs and the active engine version are hashed before research.
- Every builder receives an independent verifier.
- Failed and interrupted calls are preserved; resume creates a new call.
- Every call keeps its exact prompt, raw Codex JSONL, and conversation ID when
  emitted; each run pins the immutable engine tree that produced them.
- Later briefs cite immutable parent packet IDs.
- Reusable byproducts and memory are fallback salvage, not objectives,
  completion, progress, or harness-quality signals.
- Problem-memory reconciliation happens after research.
- Harness reflection happens once, after memory, and cannot change its own run.
- A candidate changes at most one engine file and cannot activate itself.
- The launcher activates only between runs and rolls back process or invariant
  failures.
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
bb run navier-stokes problems/navier-stokes/goals/my-goal.edn
bb campaign 10 navier-stokes problems/navier-stokes/goals/my-goal.edn
bb campaign-resume <campaign-id>
```

`campaign` runs sequentially and atomically records round IDs and exact version
lineage under `.forge/campaigns/`. `campaign-resume` continues an interrupted
campaign, first resuming any interrupted round. A tested candidate can become
active only after its generating round closes, so it first affects the
following round.
The next run ID is persisted before launch. If a probationary engine fails, its
run remains as evidence while the launcher restores the parent and retries that
campaign round with a new run ID.
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
problems/<id>/            problem-local targets, goals, memory, and results
templates/goal.edn        bounded goal contract
.forge/runs/<id>/         ignored append-only local evidence
.forge/exports/<id>/      ignored static bundles
```

Git versions the public source, while the launcher snapshots each active
engine. A descendant may change topology, roles, tools, search policy, or
allocation. Reflection receives a call-local copy plus the prior evolution
history, run record, and local memory candidate. It may edit one file and must
name the evidence, expected benefit, regression risk, and frozen endpoint-line
benchmark. A mutation that rewards partial-work volume or future usefulness is
a regression.

The launcher owns `.forge/CURRENT.edn`, immutable versions under
`.forge/versions/`, and append-only `.forge/activations/`. It runs only fixed
validation and smoke tests after a candidate's generating run closes,
activates only for a later run, and restores the last known-good version after
an invariant or process failure. The model's benchmark field is descriptive,
never an executable command. Problem-local
`.forge/memory/<problem-id>/INDEX.edn` carries candidate context across runs
but never admits mathematics.

That index stays small: it keeps at most 32 recent evidence pointers and terse
behavior-changing deltas. Full derivations, failures, prompts, raw events, and
artifacts remain in the referenced `.forge/runs/<id>/` tree as searchable
long-form memory. The problem pack's curated `memory/KEY_LEARNINGS.md` is the
always-on layer.

## Truth boundary

The repository begins with packs for the seven Clay Millennium Prize Problems;
six remain open and Poincaré is retained as a solved reference. Pack summaries
do not replace official problem statements.

Repository acceptance is not recognition by the Clay Mathematics Institute.
Mathematical admission requires the external mechanism declared by the problem
pack, normally formal checking or qualified independent human review.

The local runner is suitable for trusted research checkouts. It never executes
contributor-supplied bundle code during ordinary inspection.
