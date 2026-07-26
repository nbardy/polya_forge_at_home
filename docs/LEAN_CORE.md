# Lean core

Forge is one fixed research loop over plain files:

```text
plan → parallel build/verify → freeze packets → repeat
                                          ↓
                              memory → reflection
                                           ↓
                           inert candidate → test → next run
```

The controller keeps only mechanisms that protect a reproduced invariant or
directly change the research graph:

- one frozen endpoint objective and first missing implication per run;
- finite calls, fan-out, and one global deadline;
- frozen inputs and a hash of the active engine;
- independent verification;
- content-linked successor packets;
- preserved failures and deterministic resume;
- memory before harness reflection;
- one-file candidate diffs and between-run activation;
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
validation and smoke suite before it may copy the candidate to
`.forge/versions/vNNNN/engine` and activate it for the next run. The model's
benchmark field is a falsifiable description, never a command. Each decision
and mutation rationale is append-only under `.forge/activations/`. An
invariant or process failure restores the prior known-good version before
another run starts.

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
