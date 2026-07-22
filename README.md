# Pólya Forge at Home

> **Swarm-based test-time compute for humanity's hardest problems.**

Pólya Forge at Home is an open, local-first system for turning difficult
mathematical problems into long-running, auditable swarm-compute programs.
People contribute compute from home; bounded AI research swarms explore and
verify exact claims; asynchronous merge turns useful work into shared problem
memory; and the harness recursively learns how to attack each problem better.
The multi-user network is the roadmap; the v0.2 candidate is the tested local
iterative node and bundle protocol from which that network can be built.

It does **not** decide that a proof is correct. It runs bounded research rounds,
records exact inputs and outputs, requires an independent verification branch,
and exports a contribution bundle that other people can inspect and reproduce.

The repository begins with problem packs for all seven Clay Millennium Prize
Problems. Six remain open; the Poincaré conjecture is retained as a solved
reference and future benchmark. The concise `target.md` files are navigation
documents, not substitutes for the official Clay problem descriptions.

## The thesis

The best harness is a harness that evolves itself.

Harness design—like coding, model training, theorem proving, falsification, and
mathematical research—is itself a problem for AI models. Instead of freezing
one human-designed workflow and pointing it at every problem, Forge treats the
harness as another versioned research object. Every completed round reviews
which prompts, topology, memory, verification strategy, and resource allocation
helped or hurt. It then proposes the next problem-adapted harness, with evidence,
regression risks, and a cheapest test.

This is **recursive harness optimization**: scientific work improves the
harness, and the improved harness performs the next scientific work. The
public engine records successor proposals but deliberately requires separate
validation before activation.

## Two axes of test-time compute

Forge is designed to scale inference in two complementary directions:

1. **Long-horizon test-time compute:** let a reasoning process work longer,
   revisit failures, use tools, preserve state, and continue across hours,
   days, or months. OpenAI reported that o1 performance improved smoothly with
   more time spent thinking at test time.
2. **Parallel test-time compute:** run diverse candidate derivations,
   falsifiers, source checks, formalizations, and independent verifiers at the
   same time, then select or merge only evidence that survives review.

[Noam Brown's 2024 inference-scaling slides](https://simons.berkeley.edu/sites/default/files/2024-11/LLM24-1%20Slides%20-%20Noam%20Brown.pdf)
put the opportunity plainly: “There is still room to push inference compute
much further.” [OpenAI's o1 report](https://openai.com/index/learning-to-reason-with-llms/)
documents the long-horizon scaling signal. The DeepMind-affiliated ICLR paper
[Scaling LLM Test-Time Compute Optimally](https://proceedings.iclr.cc/paper_files/paper/2025/hash/1b623663fd9b874366f3ce019fdfdd44-Abstract-Conference.html)
studies parallel best-of-N sampling alongside adaptive search and revision.

## From one laptop to a research civilization

One user can run a bounded local swarm. Thousands of users can run many
problem-scoped swarms asynchronously:

```text
shared problem frontier
  -> leased claim cells
  -> local swarms running in parallel
  -> content-addressed evidence bundles
  -> static validation and de-duplication
  -> independent audit or formal checking
  -> asynchronous merge into shared results
  -> new frontier + better problem-adapted harness
```

The unit of collaboration is not a chat transcript. It is a reproducible,
hash-addressed claim packet with its failures and verification status intact.
That lets grand problems persist beyond one context window, machine, model,
maintainer, or weekend.

The north star is to make any problem that can be decomposed, explored, and
checked attackable as a scalable compute program. This is an ambition, not a
claim that compute alone guarantees a solution. Swarms can amplify correlated
errors as easily as insight, so verification, provenance, diversity, and
problem-specific harness adaptation are part of the scaling system—not
afterthoughts.

## What works in the v0.2 candidate

- Discover and validate multiple problem packs.
- Validate bounded goal contracts.
- Run bounded, iterative `manage -> parallel execute/verify -> review` waves
  with Codex, followed by one durable memory reconciliation.
- Continue only when an independent verifier and a positive Pólya receipt
  support a non-duplicative next research wave.
- Keep agent work inside per-run attempt directories.
- Checkpoint each stage and resume interrupted rounds.
- Hash the problem pack, goal, prompts, schemas, and generated artifacts.
- Export sanitized, non-executable contribution bundles.
- Inspect bundles without executing contributor code.
- Keep mathematical results separate from harness-change proposals.

The runner is an alpha. Its process isolation is suitable for trusted local
research checkouts, not for executing hostile third-party code. Maintainers run
`bb validate`, `bb test`, and `bb inspect` explicitly; this repository does not
use GitHub Actions.

## Lean by design

The local core is one executable controller, `engine/forge.clj`. Model-facing
research prompts remain external, versioned Markdown because prompt changes
must be as easy to audit and merge as code changes. Each run freezes its inputs
and engine hash; recursive improvement produces a proposed descendant rather
than mutating the active parent in place. Git supplies the initial asynchronous
collaboration layer, so the alpha needs no database, server, or job queue.

See [`docs/LEAN_CORE.md`](docs/LEAN_CORE.md) for the complexity budget, file
boundaries, descendant lifecycle, and intended future candidate directory.

## Requirements

- [Babashka](https://babashka.org/) available as `bb`
- [Codex CLI](https://developers.openai.com/codex/cli/) available as `codex`
- Git

## Quick start

```bash
bb doctor
bb problems
bb validate
bb test
```

Create a bounded goal from [`templates/goal.md`](templates/goal.md), place it
under the selected problem's `goals/` directory, and inspect the plan:

```bash
bb dry-run navier-stokes --goal problems/navier-stokes/goals/my-goal.md
```

Run one research round:

```bash
bb run navier-stokes --goal problems/navier-stokes/goals/my-goal.md
```

An active goal may use up to eight evidence-driven waves in one run. Wall time
is a true global deadline, not a per-call allowance. Longer runs come from
verified successor work, never artificial waiting or repeated prompts.

If the controller is interrupted:

```bash
bb runs
bb resume <run-id>
```

Export a finished run and validate the resulting bundle:

```bash
bb export <run-id>
bb inspect .forge/exports/<bundle-id>
```

## Repository map

```text
engine/                  Generic runner, kernel, prompts, and schemas
problems/<problem-id>/   Independent mathematical problem packs
schemas/                 Public problem, goal, and contribution contracts
templates/               Human-facing authoring templates
docs/                    Architecture, trust model, and contribution process
.forge/                  Ignored local runs and exports
```

Read [`docs/SPEC.md`](docs/SPEC.md) before changing the engine and
[`docs/CONTRIBUTIONS.md`](docs/CONTRIBUTIONS.md) before submitting research.
The deliberately small core architecture is defined in
[`docs/LEAN_CORE.md`](docs/LEAN_CORE.md).
The distributed multi-user architecture is specified in
[`docs/DISTRIBUTED_SWARMS.md`](docs/DISTRIBUTED_SWARMS.md).

## Truth and prize boundary

A Forge run is evidence about a research process, not proof of its mathematical
conclusion. Result states are deliberately separated:

```text
submitted -> validated -> independently reproduced -> accepted-searchable
          -> admitted
```

`admitted` requires the problem pack's declared external gate, normally a
kernel-checked formalization or qualified external human review. Repository
acceptance is not recognition by the Clay Mathematics Institute. CMI does not
accept direct submissions and applies its own publication, waiting-period, and
community-acceptance rules.

## Project status

This is a new public-platform extraction from the original Navier–Stokes
Pólya Forge. The generic engine is usable for bounded local experiments, but
has not yet earned a public beta designation. See [`docs/ROADMAP.md`](docs/ROADMAP.md).
