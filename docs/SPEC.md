# Pólya Forge at Home specification

## 1. Purpose

Pólya Forge at Home improves the rate of independently checkable mathematical
progress. It converts a bounded goal into immutable briefs, private execution
attempts, independent audits, a memory proposal, a run review, and an
exportable evidence bundle.

The system is a research operating system. It is not a theorem prover, a
publication venue, an autonomous truth authority, or a representative of any
prize organization.

## 2. Information flow

```text
problem pack + bounded goal
        |
        v
      manage            ambiguity reduction / next-wave selection
        |
        v
  execute branches      bounded parallel derivation/falsification
        |
        v
      verify             independent audit of each builder
        |
        v
      review             Pólya receipt + engine-change proposal
        | positive receipt backed by an independent PASS
        +-------------------------> next manage wave
        |
        v
     remember            terminal problem-local memory proposal
        |
        v
  sanitized bundle      public, non-executable contribution unit
```

Management and review are convergence points. Execution is parallel only after
the manager has emitted exact task contracts. Roles are capabilities rather
than handicapped personas; the immutable brief controls scope.

The local state machine is the atomic worker of a larger distributed design.
Many users may run independent cells concurrently and submit immutable bundles
to an asynchronous merge queue. See `DISTRIBUTED_SWARMS.md`.

## 3. Stable invariants

1. Every run is finite and names exactly one problem and goal.
2. A run fingerprints its problem pack, goal, prompts, and schemas.
3. Workers write only into their attempt directories.
4. Every mathematical builder receives an independent verifier.
5. Failures are evidence and are never silently repaired.
6. Model agreement does not admit a claim.
7. Problem memory and engine memory are separate.
8. Old runs and exported bundles are immutable.
9. Public bundle inspection never executes contributed code.
10. Recursive engine evolution is proposed by a run but activated separately.

The implementation should preserve these invariants with one executable
controller and plain files for as long as that remains practical. Prompts are
external Markdown, not opaque strings embedded throughout the controller.
Candidate self-modification creates a complete versioned descendant and never
edits the active parent in place. See `LEAN_CORE.md`.

## 4. State machine

```text
CREATED -> MANAGE -> EXECUTE_VERIFY -> REVIEW --+-> MANAGE
                     |                 |         |
                     |                 +-> REMEMBER -> CLOSED
                     +----------------------- FAILED
```

Each completed role has a durable artifact. Every wave freezes its manager
allocation, builder/verifier branches, and review before a successor wave may
start. Resume starts at the first stage whose required terminal artifact is
absent. Failed attempts are retained;
rerunning them creates a new attempt number in a later engine version.

v0.2 supports resuming at wave and role boundaries. Mid-process event streams are
preserved but a killed model invocation itself is not resumed token-for-token.

## 5. Directory ownership

- `engine/`: maintained code and immutable versioned prompts/schemas.
- `problems/<id>/`: problem-maintainer-owned canonical research context.
- `.forge/runs/<id>/`: controller-owned local run state.
- `.forge/exports/<id>/`: sanitized export generated from a terminal run.
- worker attempt directories: single-attempt write ownership.

## 6. Run artifacts

Every run contains:

```text
RUN.edn
RUN.json
events.jsonl
snapshot/
briefs/
attempts/<brief-id>/{execute,verify}/
waves/WNN/{manager,review,wave}.json
memory/
review/
published/manifest.edn
```

Raw prompts and model event logs stay in the local run. Exports contain only
the public allowlist defined by the contribution specification.

## 7. Recursion

The reviewer may propose complete prompt, schema, topology, or runner changes.
The controller records these proposals under `review/engine_changes.json`; it does not
automatically activate them. Automatic multi-round activation remains disabled
until signed/versioned proposals, regression replay, and crash-safe activation
are implemented.

This is deliberately weaker than the original private Forge's finite
auto-activation mode. Public distribution increases the cost of evaluator
capture or malicious self-modification, so activation is a separate trust
decision.

The intended successor is **recursive harness optimization**: maintain a
stable global safety kernel while allowing each problem pack to evolve its own
manager, prompts, topology, falsifiers, memory policy, tools, and compute
allocation. Candidate versions compete against frozen fixtures and later run
receipts; they do not validate themselves.

## 7.1 Swarm-based test-time compute

Forge treats long-horizon and parallel inference as separate budget axes.
Sequential cells extend reasoning duration through durable checkpoints.
Parallel cells increase candidate and verifier diversity after the work
contract freezes. Distributed contributors extend both axes across machines
and calendar time while content-addressed bundles preserve a single auditable
scientific history.

## 8. Claim maturity

Allowed descriptive states include:

```text
question
heuristic
conjecture
proved-lemma
known-theorem
computational-evidence
refuted
unresolved
```

Repository workflow states are separate:

```text
submitted
validated
independently-reproduced
accepted-searchable
admitted
repaired
retired
quarantined
rejected
```

## 9. Determinism

The controller, hashes, schemas, fixture outputs, and bundle inspection should
be deterministic. Language-model research output is not expected to be
byte-identical. Reproducibility means reconstructing exact inputs, provenance,
budgets, and artifacts and then independently checking the mathematical claim.

## 10. Non-goals for the local alpha

- Executing untrusted contributor code during validation
- Automatically merging results or memory
- Automatically activating recursive engine code
- Distributed queues or a central hosted scheduler
- Cryptocurrency, voting, reputation-weighted truth, or prize allocation
- Claiming that all seven Millennium problems remain open
