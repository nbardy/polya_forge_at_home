# Pólya Forge at Home

**Folding@home for open math problems—and for the AI research harness itself.**

Donate idle agent compute to a bounded mathematical research campaign. A swarm
plans attacks, runs them in parallel, independently checks the results, keeps
useful failures, and proposes interesting partial results for review. The same
network also learns how to improve the shared harness: successful strategies
can become tested harness proposals that help every problem, or a declared
family of problems.

This is especially useful when you have agent capacity left at the end of the
week—such as unused tokens on a subscription—and would rather spend it on a
public, auditable research attempt.

Pólya Forge produces evidence, not truth by consensus. A merged result is not
a proof, and repository acceptance is not recognition by a prize body.

## Give an agent a problem

Requirements: Babashka, Git, and Codex CLI 0.138 or newer.

```bash
bb check
bb run problems/erdos-647/goals/find-witness.edn
```

That launches one finite campaign against the exact goal and budget in the
goal file. To donate a longer block of idle compute:

```bash
bb campaign 10 problems/erdos-647/goals/overnight-witness.edn
```

You can create another bounded goal from [`templates/goal.edn`](templates/goal.edn),
including one under [`problems/navier-stokes/`](problems/navier-stokes/).

## What the swarm does

```text
one frozen math goal
        ↓
plan exact attacks
        ↓
parallel builders → independent verifiers
        ↓
preserve results, counterexamples, and failed routes
        ↓
update problem-local learning by evidence pointer
        ↓
reflect once on the harness
        ↓
test a new harness challenger without rewriting history
```

Agents collaborate at two levels:

- **Mathematical learning** stays under `problems/<problem>/`. Local attempts
  have UUIDs under `.forge/runs/`; accepted public proposals live under
  `runs/<problem>/<uuid>/`.
- **Harness learning** may apply to one problem family or all problems. Local
  immutable harness versions live under `.forge/versions/vNNNN`; shared
  proposals use Git plus a small receipt under `harness_history/<uuid>/` so the
  code is not duplicated in version folders.

The architecture follows the familiar planning, memory, and tool-use model
described in Lilian Weng's
[LLM Powered Autonomous Agents](https://lilianweng.github.io/posts/2023-06-23-agent/),
then adds independent verification, immutable evidence, distributed review,
and selection over harness versions.

## Share something interesting

Most local iterations should never become pull requests. A proposal is worth
sharing when it contains a new independently checked result, a reproducible
counterexample or route retirement, a meaningful reduction of the exact open
line, or a harness change that wins a frozen endpoint benchmark.

To publish one terminal research run and open a narrowly scoped PR:

```bash
bb propose <run-uuid>
```

`bb propose` refuses a dirty tree, validates and sanitizes the research bundle,
creates `runs/<problem>/<uuid>/`, commits only that bundle on a proposal branch,
pushes it, and opens a GitHub pull request. It never publishes raw model events,
local memory, credentials, or harness state.

The exact accept/synthesize/reject rules are in
[`docs/PROPOSALS.md`](docs/PROPOSALS.md). Research and harness changes always
use separate PRs.

## Run the research lead

[`research_lead/PROMPT.md`](research_lead/PROMPT.md) is the maintainer prompt
for reviewing proposal PRs. Point a fresh Codex task at that file to:

- inspect bundles without executing contributor code;
- deduplicate UUIDs, claims, and evidence;
- accept, reject, or synthesize overlapping proposals;
- update the newest problem-local learning pointers;
- separate problem learning from cross-problem harness learning; and
- benchmark harness learners before they can become the active champion.

Successful, independently verified work is the strongest positive signal.
Failures remain valuable for retiring exact routes and avoiding repetition,
but volume of failure or partial output never counts as harness progress.

## Local history versus shared history

```text
.forge/runs/<uuid>/                 every local attempt; ignored by Git
.forge/memory/<problem>/            local cross-run problem memory
.forge/versions/vNNNN/              full local immutable harness versions
.forge/activations/                 local promotion and rollback receipts

runs/<problem>/<uuid>/              accepted or proposed public research
problems/<problem>/memory/          curated shared problem learning
harness_history/<uuid>/             shared harness proposal receipts
engine/memory/KEY_LEARNINGS.md      current cross-problem process learning
Git commits                         canonical shared harness source history
```

This repository is the lean successor to the much larger experimental
Navier–Stokes workspace. The controller is small; the fixed launcher is larger
because it owns process cleanup, sandbox boundaries, immutable versioning,
publication, and rollback. Those authority checks stay outside the
self-modifying engine.

## Safety and truth boundary

- One run touches one problem and one exact bounded goal.
- Builders cannot verify or admit their own work.
- Claims retain explicit status, assumptions, failures, and remaining checks.
- Imported theorems require primary-source checking before carrying a proof.
- Partial work may be shared as evidence but is never silently labeled progress.
- Harness changes cannot activate themselves; benchmark ties and losses retain
  the champion.
- Ordinary review never executes contributor-supplied code.

Technical details live in [`docs/SPEC.md`](docs/SPEC.md),
[`docs/TRUST_MODEL.md`](docs/TRUST_MODEL.md), and
[`docs/LEAN_CORE.md`](docs/LEAN_CORE.md).
