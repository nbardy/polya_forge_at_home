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
```

There is no model progress gate. Verification fixes the evidence; the next
planner either cites audited packets and continues or stops. Model calls,
parallel fan-out, and wall time are finite.

## Core invariants

- One run touches one problem pack and one active goal.
- Exact inputs and the checked-out harness are hashed before research.
- Every builder receives an independent verifier.
- Failed and interrupted calls are preserved; resume creates a new call.
- Later briefs cite immutable parent packet IDs.
- Problem-memory reconciliation happens after research.
- Harness reflection happens once, after memory, and cannot change its own run.
- `PASS` is an internal audit verdict, never mathematical admission.

## Run it

Requirements are Babashka, the Codex CLI, and Git.

```bash
bb check
bb test
bb run navier-stokes problems/navier-stokes/goals/my-goal.edn
```

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

Git versions the complete harness. A proposed descendant may change topology,
roles, tools, search policy, or allocation, but it must beat a frozen benchmark
before merge. The runtime does not pretend that prompt versions can replace an
incompatible controller.

## Truth boundary

The repository begins with packs for the seven Clay Millennium Prize Problems;
six remain open and Poincaré is retained as a solved reference. Pack summaries
do not replace official problem statements.

Repository acceptance is not recognition by the Clay Mathematics Institute.
Mathematical admission requires the external mechanism declared by the problem
pack, normally formal checking or qualified independent human review.

The local runner is suitable for trusted research checkouts. It never executes
contributor-supplied bundle code during ordinary inspection.
