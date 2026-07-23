# Lean core

Forge is one fixed research loop over plain files:

```text
plan → parallel build/verify → freeze packets → repeat
                                          ↓
                              memory → reflection
```

The controller keeps only mechanisms that protect a reproduced invariant or
directly change the research graph:

- finite calls, fan-out, and one global deadline;
- frozen inputs and a hash of the whole checked-out harness;
- independent verification;
- content-linked successor packets;
- preserved failures and deterministic resume;
- memory before harness reflection;
- static, non-executing export.

There is deliberately no database, scheduler, hosted service, progress-score
taxonomy, generic graph DSL, automatic activation, or GitHub Actions workflow.

Raw calls preserve process evidence; one immutable packet is the canonical
reusable result of each verified branch. Git versions the controller, prompts,
schemas, and root rules together.
A descendant earns merge by fixing a reproduced bug or beating a frozen
benchmark through a structural change—not through more prose, labels, or model
agreement.

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
