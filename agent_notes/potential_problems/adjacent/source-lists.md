# Adjacent sources of tractable conjectures

The goal is not to ingest every famous open problem. It is to find problems
with a frozen statement, a plausible first missing implication, and an
independent verification path.

## Priority sources

| Source | Scale / character | Best use | Main risk |
|---|---|---|---|
| [Formal Conjectures](https://github.com/google-deepmind/formal-conjectures) | Thousands of Lean-formalized statements across research, olympiad, and textbook categories | Filter to `research open` statements already close to `mathlib`; pair natural-language and Lean gates | Formal statement may not perfectly match the original conjecture; openness can change |
| [OEIS](https://oeis.org/) comments and conjectures | Huge collection of integer-sequence observations | Finite counterexample searches, recurrence identities, inequalities, exact-value conjectures | Many claims are informal, unattributed, or already known elsewhere |
| [Open Problem Garden](https://www.openproblemgarden.org/) | Broad community wiki by subject | Discover small named questions and follow citations to primary sources | Stale status and uneven curation |
| [The Open Problems Project](https://topp.openproblem.net/) | Discrete and computational geometry | Construction and counterexample searches with computational geometry verifiers | Continuous configurations make rigorous certification hard |
| [Kourovka Notebook](https://kourovka-notebook.org/) | Curated group-theory problem collection | Small finite-group and computational algebra questions | Many problems require deep specialist infrastructure |
| [AIM problem lists](https://aimath.org/problemlists/) | Workshop-generated specialist lists | Narrow, well-contextualized questions with expert references | Often technically deep despite concise statements |
| [UnsolvedMath](https://www.unsolvedmath.com/) | Aggregates Erdős, Open Problem Garden, Kirby, Kourovka, and other collections | Discovery index and deduplication aid | Secondary aggregator; never sufficient as the authoritative source |

## Candidate families similar to the tractable Erdős subset

### Exact finite-witness searches

- OEIS conjectures asking whether a sequence ever hits, misses, repeats, or
  violates a simple inequality.
- Small graph/hypergraph colorings with a compact adjacency or coloring
  certificate.
- Covering systems, exact covers, difference sets, and combinatorial designs.
- Integer tuples satisfying exact divisibility, perfect-power, or unit-fraction
  constraints.

### Counterexample searches with compact certificates

- Unimodality, log-concavity, and real-rootedness claims for graph polynomials.
- Extremal graph inequalities where a violating graph and an independently
  computed invariant suffice.
- Small combinatorial optimization conjectures expressible as SAT, SMT, MILP,
  or exhaustive isomorph-free generation.
- Recurrence and inequality conjectures where exact rational/integer
  evaluation avoids numerical ambiguity.

### Proof targets close to formal libraries

- `Formal Conjectures` entries using definitions already in `mathlib`.
- Finite combinatorics statements whose final gate can be a kernel-checked
  certificate.
- Elementary inequalities or divisibility lemmas with a short dependency
  chain and a separately audited natural-language statement.

## Intake filters

Prefer candidates with all of:

1. a primary source and a recent literature trail;
2. an exact, unambiguous statement;
3. a witness, counterexample, proof object, or deterministic checker;
4. a first missing implication narrow enough for one bounded campaign;
5. low-cost negative evidence that is still useful when no solution appears;
6. no requirement to execute untrusted contributor code during validation.

Reject or defer candidates dominated by:

- ambiguous wording or disputed intended meaning;
- an ineffective “sufficiently large” theorem with no practical finite bound;
- continuous numerical optimization without rigorous interval certificates;
- famous problems whose known verified ranges dwarf home compute;
- dependence on a large unformalized specialist theory before the first open
  edge is even reached.

## Next catalog expansion

The best next import is not another raw thousand-row list. It is a filtered
snapshot of `Formal Conjectures` containing only current `research open`
entries, annotated with statement dependencies, proof status, and estimated
verification cost. A second useful import would be OEIS conjectures with
machine-checkable integer predicates and no recorded proof.
