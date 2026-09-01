# Lean core

Forge is one fixed research loop over plain files:

```text
plan → parallel durable build/fresh verify → freeze packets → repeat
                         └ whole endpoint → candidate pause
                                          ↓
                              memory → reflection
                                           ↓
                 inert candidate → compatibility gate → probation
                                     └ blinded tournament → confirm/rollback
```

The controller keeps only mechanisms that protect a reproduced invariant or
directly change the research graph:

- one frozen endpoint objective and first missing implication per run;
- finite calls, fan-out, and one global deadline;
- frozen inputs and a hash of the active engine;
- durable builder continuity for one exact verifier-repair lineage;
- fresh independent verification that never inherits the builder thread;
- content-linked successor packets;
- preserved failures and deterministic resume;
- memory before harness reflection;
- whole-endpoint candidate pause before external admission;
- one-file candidate diffs, probation, and benchmarked promotion;
- static, non-executing export.

There is deliberately no database, scheduler, hosted service, progress-score
taxonomy, generic graph DSL, in-run activation, or GitHub Actions workflow.

Raw calls preserve process evidence; one immutable packet is the canonical
evidence record of each verified branch. A packet is not progress merely
because it is reusable. Git versions the public source; the launcher snapshots
each active engine, while root rules freeze with run input. A descendant earns
merge by fixing a reproduced bug or increasing verified movement on the same
frozen endpoint line through a structural change—not through more packets,
partial artifacts, future-use claims, prose, labels, or model agreement.

## Recursive evolution boundary

The active version in `.forge/CURRENT.edn` is immutable for its whole run.
After research and memory reconciliation, reflection receives the frozen run
record, prior activation history, local memory candidate, and a call-local
`candidate/engine`. It may change at most one file there; its structured
reflection result declares the mutation.

The launcher—not the engine or reflecting model—locates that exact reflection
call, checks its declaration and single diff, and runs only its fixed
compatibility gate before it may copy the candidate to
`.forge/versions/vNNNN/engine` as one probationary challenger. The active
`CURRENT` pointer remains on the champion. The model's benchmark field is a
falsifiable description, never a command. A launcher-owned matched blinded
tournament confirms only a strict increase in independently admitted whole
endpoints; ties and losses roll back. Each decision and mutation rationale is
append-only under `.forge/activations/`.

Builder continuity is deliberately narrower than general agent memory. A
fresh build creates a durable app-server thread. A descendant naming one
endpoint-bearing `REPAIR` resumes precisely that thread with the complete
candidate and verifier defect, while writing only in a new call directory.
Planner, verifier, memory, and reflection calls stay fresh; in particular, a
verifier cannot reuse the builder conversation.

Likewise, `endpoint_disposition = CANDIDATE` is a stop signal, not a truth
label. It requires an independently passing complete endpoint, closes the run
with admission pending, and prevents another campaign round while the pack's
declared external gate is applied.

Both candidate checks and active versions run inside the fixed
`polya-forge-engine` Codex permission profile. Evolvable code can write run,
same-problem memory, export, and temporary state, but not prior runs, the
launcher, source tree, versions, pointer, or activation receipts. The launcher
scrubs inherited
environment variables and fails closed if the OS cannot enforce the profile.

Problem memory under `.forge/memory/<problem-id>/INDEX.edn` is likewise a local
cross-run salvage index. It may prevent repeated work but never becomes a
research objective, counts as endpoint progress, or raises a mathematical
claim's admission status.

The production code is separated by boundary, not by framework layer:

```text
kernel/launcher.clj      fixed authority, version, and sandbox boundary
engine/forge.clj          command dispatch
engine/forge/core.clj     research and resume
engine/forge/bundle.clj   export and static inspection
```

Tests and fake model responses live outside the production controller.

Before adding machinery, ask:

1. Which reproduced failure or frozen benchmark justifies it?
2. Can an existing packet, budget, verifier, or file convention express it?
3. Does it create a second source of truth?
4. Can one contributor still reconstruct the complete run in one sitting?
