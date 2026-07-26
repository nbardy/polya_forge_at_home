# Contributor and agent instructions

Pólya Forge at Home produces research evidence, not truth by consensus.

## Design philosophy

The official endpoint is the sole research objective and the sole measure of
harness quality. Every goal and every research brief must directly attack the
goal's frozen first missing implication. Fan-out may try genuinely different
strategies, roles, or graph structures, but every branch must remain on that
same implication.

Reusable chunks are never planning objectives. A lemma, source audit,
formalization, computation, or other useful byproduct recovered from an
unsuccessful direct attack is fallback salvage only. It scores zero by itself
and must never become a goal, deliverable, completion criterion, continuation
reason, or harness-quality signal. Derive whatever the direct attack requires,
then preserve surviving partial work by pointer after the attack ends. Any
harness mutation that weakens this endpoint-first rule degrades quality and
must fail regression and activation.

Merely completing a lifecycle, spending more tokens, creating more packets or
memory, adding labels, or lengthening prompts is not progress.

Keep the controller lean. Add machinery only when it fixes a reproduced defect,
protects a non-negotiable invariant, or improves a frozen benchmark. Prefer a
small number of composable mechanisms—bounded iteration, evidence-driven
fan-out, independent verification, durable memory, and explicit stopping
rules—over special cases, status taxonomies, and orchestration ceremony.

Harness evolution must be empirical and structural. Candidate descendants may
change topology, role allocation, tool use, search policy, or resource
allocation when a stated hypothesis and regression comparison justify it.
They must not claim improvement from extra prose, extra signals, self-review,
or model agreement alone. Preserve a simple stable kernel; version and test
every evolved harness outside its parent before activation.

Research comes first. A successor brief must cite frozen audited parent
packets; an empty plan or exhausted budget stops research. Harness reflection
runs once, after all research waves and terminal memory reconciliation. It may
propose a Git-versioned descendant but cannot alter the run that generated its
evidence or activate its own proposal.

## Non-negotiable rules

1. Every run has one problem pack, one bounded goal, and finite budgets.
2. Label every claim as question, heuristic, conjecture, proved lemma, known
   theorem, computational evidence, refuted, or unresolved.
3. Preserve failed steps. Repairs create descendants; they do not rewrite the
   failed artifact.
4. A builder cannot verify or admit its own result.
5. Imported theorems must be checked against primary sources before they carry
   a proof.
6. Mathematical admission requires the gate in `problem.edn`. Model agreement
   is never sufficient.
7. Workers write only inside their assigned attempt directory. The controller
   is the sole canonical run writer.
8. Do not modify another problem pack while executing a goal.
9. Engine-wide memory contains process lessons only. Mathematical lessons stay
   inside their problem pack.
10. Never claim that repository acceptance satisfies an external prize body's
    rules.
11. Every planned brief must preserve its goal's exact objective, endpoint
    edge, and first open line. Alternate strategies may vary; side objectives
    may not.

## Required research output

Every attempt must report its objective, inputs and exclusions, derivation or
experiment, failed steps, assumptions, verification still needed, exact claim
status, and next actions. The engine preserves the structured response and raw
event log inside the local run.

## Pull requests

Research contributions and engine changes must use separate pull requests.
Ordinary result validation must never execute contributor-supplied code.
