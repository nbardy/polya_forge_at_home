# Distributed swarm and recursive-optimization specification

## 1. Vision

Pólya Forge at Home pools test-time compute across people, machines, models,
and time. A grand problem becomes a persistent research program whose state is
externalized into exact claims, evidence, audits, and dependency edges.

The system scales along three dimensions:

- **Depth:** longer sequential reasoning and tool-use trajectories.
- **Breadth:** more diverse parallel attempts, attacks, and verifiers.
- **Duration:** asynchronous work that continues across days, months, model
  generations, and contributor turnover.

Compute volume is not the objective. The objective is verified dependency-graph
change per unit of compute and reviewer attention.

## 2. Scientific and optimization loops

Forge contains two coupled loops:

```text
scientific loop
  target -> frontier -> claim cell -> execute -> verify -> shared result

optimization loop
  completed runs -> compare receipts -> diagnose harness -> candidate version
                 -> regression fixtures -> scoped activation -> later receipt
```

The scientific loop tries to advance the problem. The optimization loop tries
to improve the machinery that selects, executes, verifies, remembers, and
merges future work.

Harness changes are hypotheses. They require evidence and evaluation just like
mathematical claims. A harness is not better because it is newer, larger, more
agentic, or more expensive.

## 3. Problem-adapted harnesses

Each problem begins from a stable global engine and may evolve a problem-local
profile:

```text
engine/versions/vNNNN/                 shared safety and execution kernel
problems/<id>/harness/versions/vNNNN/  problem-specific prompts and topology
```

A local profile may adapt:

- decomposition and manager prompts;
- divergence versus convergence topology;
- source-verification and formalization tools;
- falsifier libraries and benchmark fixtures;
- memory retrieval and sealed-context policy;
- verifier allocation and proof-withholding strategy;
- compute allocation by uncertainty and expected information gain;
- merge rules for the problem's claim types.

It may not weaken global provenance, finite-budget, single-writer,
non-executing-intake, independent-verification, or admission boundaries.

A problem-local improvement can be proposed upstream only after evidence that
it generalizes. Mathematical memory never becomes global merely because it was
useful in one pack.

## 4. Distributed topology

### 4.1 Registry and frontier service

The registry publishes:

- problem-pack versions and hashes;
- active harness versions;
- frozen goals and claim cells;
- dependency edges and conflicting claim families;
- available work leases and declared budgets;
- accepted bundle, audit, retirement, and admission receipts.

The registry coordinates work but cannot declare a theorem true.

### 4.2 Contributor node

A home node:

1. synchronizes a signed problem and harness snapshot;
2. claims a finite work lease or opens a locally funded incubation branch;
3. runs a bounded local swarm;
4. checkpoints state without sharing scratch reasoning;
5. exports a sanitized contribution bundle;
6. submits the bundle and releases the lease.

Nodes may differ in hardware, model provider, formal tools, and availability.
Every difference is provenance, not noise to hide.

### 4.3 Content-addressed bundle store

Bundles are immutable and addressed by hashes. Large raw telemetry may live in
an artifact store; Git contains manifests, result cards, audits, and stable
locators. Identical inputs or outputs can be de-duplicated without collapsing
independent provenance.

### 4.4 Asynchronous merge queue

Merge is a typed research operation, not a textual Git merge:

```text
SUBMITTED
  -> STATIC_VALID
  -> PROVENANCE_VALID
  -> DE-DUPED / CONFLICT-LINKED
  -> INDEPENDENTLY_AUDITED
  -> ACCEPTED_SEARCHABLE
  -> ADMITTED (only through the problem's external gate)
```

The merge coordinator reads verifier artifacts before builder narratives. It
may accept a refutation, correction, benchmark, or side theorem without
accepting the builder's intended conclusion. Conflicting bundles remain linked
until an exact counterexample, proof, formal check, or qualified external audit
resolves the claim.

## 5. Work cells and leases

A distributable work cell contains:

- stable problem, goal, claim, and harness IDs;
- one exact deliverable and first open line;
- endpoint/dependency edge;
- input paths and hashes;
- excluded context;
- cheapest falsifier;
- completion and kill criteria;
- compute, time, and descendant budgets;
- required verifier and output types;
- lease expiry and duplicate-work policy.

Leases reduce accidental duplication; they do not create ownership of an idea.
High-value claims may intentionally receive independent redundant attempts.

## 6. Long-horizon execution

Long runs are finite sequences of checkpointed cells, never one unbounded model
conversation. Durable state includes:

- exact current claim and first open line;
- completed and failed attempt hashes;
- tool and source provenance;
- unresolved assumptions;
- next authorized cells;
- budget consumed and remaining;
- last Pólya receipt;
- active harness version.

A controller crash, contributor departure, or model upgrade cannot erase the
scientific state. A successor resumes from accepted artifacts, not hidden chain
of thought.

## 7. Parallel test-time compute

Parallelism is allocated after the claim contract is frozen. Useful branch
types include:

- independent derivations;
- adversarial counterexample search;
- source and prerequisite verification;
- computational experiments;
- formalization and statement-fidelity audits;
- mechanism-diverse approaches;
- proof-withheld reconstruction.

More agents are not automatically better. Google research on scaling agent
systems reports benefits on parallelizable tasks but degradation on sequential
ones, including large error amplification for uncoordinated independent-agent
configurations. Forge therefore measures marginal information, correlated
failure, and verifier quality rather than agent count.

## 8. Recursive harness optimization

Every candidate change has a card:

```text
change
problem scope
hypothesis
supporting run IDs
predicted benefit
regression risk
cheapest historical fixture
live evaluation budget
rollback condition
```

Evaluation proceeds in stages:

1. Static schema and security validation.
2. Replay on frozen historical fixtures.
3. Shadow evaluation without publication authority.
4. Finite problem-local activation.
5. Comparison of later Pólya receipts and cost metrics.
6. Retain, revise, demote, or revert.

The optimization target is multi-objective:

- verified progress per compute;
- verified progress per reviewer minute;
- time to first decisive falsifier;
- independent reproduction rate;
- source and statement fidelity;
- mechanism diversity;
- recovery from crashes and stale work;
- prompt, context, and runtime cost.

No scalar score overrides mathematical admission.

## 9. Scaling across thousands of users

The eventual network must support:

- append-only event ingestion rather than shared mutable run folders;
- expiring leases and idempotent submissions;
- content-addressed de-duplication;
- problem- and claim-scoped queues;
- heterogeneous provider and hardware attestations;
- backpressure based on audit capacity;
- quotas, cost visibility, and energy reporting;
- contributor attribution and conflict disclosure;
- abuse, spam, and prompt-injection quarantine;
- asynchronous formal and human review.

Review capacity is a first-class resource. The network must not generate more
unreviewable prose merely because home compute is available.

## 10. Research claim and ambition

Evidence from o1 and test-time-scaling research supports the narrower claim
that additional well-allocated inference compute can improve performance on
some reasoning tasks. It does not prove unlimited improvement, general
problem-solving, or eventual success on every mathematical problem.

Forge's ambition is stronger than its current evidence: combine longer
reasoning, broader verified search, persistent public memory, recursive harness
optimization, and distributed home compute until problems previously too large
for one person, model, or institution become tractable research programs.

That ambition is tested one audited claim cell at a time.

## References

- [OpenAI: Learning to reason with LLMs](https://openai.com/index/learning-to-reason-with-llms/)
- [Noam Brown: Learning to Reason with LLMs, 2024 slides](https://simons.berkeley.edu/sites/default/files/2024-11/LLM24-1%20Slides%20-%20Noam%20Brown.pdf)
- [Snell et al.: Scaling LLM Test-Time Compute Optimally](https://proceedings.iclr.cc/paper_files/paper/2025/hash/1b623663fd9b874366f3ce019fdfdd44-Abstract-Conference.html)
- [Google Research: Towards a Science of Scaling Agent Systems](https://research.google/blog/towards-a-science-of-scaling-agent-systems-when-and-why-agent-systems-work/)

