# Contributor and agent instructions

Pólya Forge at Home produces research evidence, not truth by consensus.

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

## Required research output

Every attempt must report its objective, inputs and exclusions, derivation or
experiment, failed steps, assumptions, verification still needed, exact claim
status, and next actions. The engine preserves the structured response and raw
event log inside the local run.

## Pull requests

Research contributions and engine changes must use separate pull requests.
Ordinary result validation must never execute contributor-supplied code.

