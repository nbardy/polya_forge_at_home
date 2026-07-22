# Lean core architecture

Pólya Forge at Home should remain understandable by one contributor in one
sitting. The initial architecture is deliberately **one executable harness plus
plain-text problem packs**. Git is the first collaboration database and merge
queue; v0.1 does not require a server, scheduler, or application framework.

## What belongs where

```text
engine/forge.clj                    single executable controller
engine/versions/<version>/
  version.edn                       capability and file map
  prompts/*.md                      versioned model-facing prompts
  schemas/*.json                    versioned output contracts
problems/<problem-id>/
  target.md                         exact public target
  AGENTS.md                         problem-local research rules
  goals/                            finite work contracts
  memory/                           curated problem-local lessons
  results/                          accepted catalog and retired routes
.forge/runs/<run-id>/               ignored local run state
.forge/exports/<bundle-id>/         reviewable, non-executable contribution
```

Stable orchestration strings may remain in `forge.clj`. Research prompts,
evaluation rubrics, problem statements, and learned instructions stay in
Markdown so their history and pull-request diffs are readable without parsing
code. Schemas remain separate because they are public contracts.

## The recursive loop

1. Freeze and hash the active controller version, prompts, schemas, problem
   pack, and bounded goal.
2. Run the local research swarm and preserve its exact artifacts and failures.
3. Evaluate mathematical progress separately from harness performance.
4. Emit proposed descendant code, prompt, schema, or topology changes.
5. Replay deterministic fixtures and relevant regression cases against the
   descendant.
6. Export useful research and the harness diff for human review and a pull
   request.
7. Activate a descendant only as a new version; never rewrite its parent.

The current controller fingerprints the executable harness and versioned
prompt/schema tree, and snapshots the canonical research inputs into each run.
It records successor proposals but does not automatically execute or activate
contributed code. A future auto-evolution mode may materialize a complete
candidate descendant—including `forge.clj`, prompts, schemas, provenance, and
evaluation—inside an isolated candidate directory. That candidate must not
mutate the checked-out parent in place.

## Complexity budget

New infrastructure must protect a demonstrated invariant or remove a measured
bottleneck. The core is expected to keep these safeguards:

- finite budgets and interruption/resume;
- immutable inputs, hashes, and provenance;
- independent verification;
- deterministic fixtures and static bundle validation;
- non-executing intake of public contributions;
- separate activation of recursive harness proposals.

Everything else should first be attempted with files, subprocesses, and Git.
In particular, databases, queues, hosted coordinators, plugin systems, and
automatic code activation are not prerequisites for the local alpha.

## Design test

A contributor should be able to answer these questions by reading
`engine/forge.clj`, the active `version.edn`, and the five prompt files:

- What exact context reaches each model call?
- What can each stage write?
- What makes a run finite and resumable?
- Which artifacts are public?
- How is a proposed child harness compared and promoted?

If answering them requires reconstructing a distributed framework, the local
core has become too complicated.
