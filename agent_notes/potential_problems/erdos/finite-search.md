# Erdős problems with finite-witness or finite-check structure

Source status as of 2026-07-26: 9 decidable, 7 verifiable, and 27 falsifiable.
These words have precise database meanings:

- **Decidable**: reduced in principle to a finite check.
- **Verifiable**: a finite example would prove the existential statement.
- **Falsifiable**: a finite counterexample would disprove the universal
  statement.

Finite does not imply computationally practical. Ineffective asymptotic
thresholds, continuous configurations, and enormous witness spaces are common.

## Decidable (9)

| # | Short description | At-home assessment |
|---:|---|---|
| [19](https://www.erdosproblems.com/19) | Remaining small cases of the Erdős–Faber–Lovász chromatic conjecture | **Heuristic: medium benchmark, poor discovery target.** The large-\(n\) theorem leaves finite cases, but the usable cutoff and verification encoding must be audited. |
| [475](https://www.erdosproblems.com/475) | Valid ordering of subsets of \(\mathbb F_p^\*\) with distinct partial sums | **Heuristic: medium/high.** Rich constructive search, but “all sufficiently large primes” may not supply a practical explicit cutoff. |
| [506](https://www.erdosproblems.com/506) | Minimum circles determined by \(n\) planar points | **Heuristic: reject initially.** The source itself records ambiguity in the non-degeneracy condition and a continuous search space. |
| [547](https://www.erdosproblems.com/547) | \(R(T)\le 2n-2\) for every \(n\)-vertex tree | **Heuristic: low.** Deep Ramsey/tree embedding machinery; remaining finite cases are not automatically small. |
| [551](https://www.erdosproblems.com/551) | Exact \(R(C_k,K_n)\) formula for \(k\ge n\) | **Heuristic: low/medium benchmark.** The asymptotic theorem leaves finite parameters, but exact Ramsey certification is expensive. |
| [556](https://www.erdosproblems.com/556) | \(R_3(C_n)\le 4n-3\) | **Heuristic: low/medium benchmark.** Finite remainder after large-\(n\) theorems; SAT certificates may handle only the first cases. |
| [580](https://www.erdosproblems.com/580) | Dense-half-degree graph contains every tree of order at most \(n/2\) | **Heuristic: low.** Exhaustive graph/tree quantification is enormous and the large-\(n\) cutoff needs auditing. |
| [742](https://www.erdosproblems.com/742) | Critical diameter-2 graph has at most \(n^2/4\) edges | **Heuristic: medium.** Clean finite graph statement and a large-\(n\) proof; potentially useful for counterexample search and small-case certification. |
| [848](https://www.erdosproblems.com/848) | Extremal sets with \(ab+1\) never squarefree | **Heuristic: medium/high benchmark.** The large-\(N\) theorem is recent and the statement is formalized; explicit finite bounds may still be impractical. |

## Verifiable (7)

| # | Finite witness sought | At-home assessment |
|---:|---|---|
| [7](https://www.erdosproblems.com/7) | A distinct covering system with all moduli odd | **Heuristic: high-risk search.** Simple to verify and formalized, but decades of work impose strong structural restrictions. |
| [307](https://www.erdosproblems.com/307) | Finite prime sets \(P,Q\) whose reciprocal sums have product 1 | **Heuristic: medium.** Exact arithmetic gives an excellent verifier, but any witness uses at least 60 primes and the combinatorial space is huge. |
| [364](https://www.erdosproblems.com/364) | Three consecutive powerful integers | **Heuristic: very low discovery odds.** No example below \(7.38\times10^{28}\); useful only as a search-engine stress test. |
| [366](https://www.erdosproblems.com/366) | A 2-full \(n\) for which \(n+1\) is 3-full | **Heuristic: low.** Exact verification is easy, but known computations already reach very large ranges and orientation matters. |
| [647](https://www.erdosproblems.com/647) | An \(n>24\) satisfying a divisor-function maximum inequality | **Heuristic: highest-priority witness search.** One-dimensional, exact, statement formalized, cheap independent verifier, and amenable to modular pruning. |
| [672](https://www.erdosproblems.com/672) | A length-\(\ge4\) coprime arithmetic progression whose product is a perfect power | **Heuristic: low/medium.** Exact verifier and parametrized search, but many cases through length 34 are already ruled out by deep number theory. |
| [835](https://www.erdosproblems.com/835) | A \(k+1\)-coloring of \(J(2k,k)\) for some \(k>2\) | **Heuristic: high-priority structural/CSP target.** Formalized and certificate-friendly. Existing results restrict candidates to \(k=p-1\); the first unresolved candidate after checked \(k\le8\) is \(k=10\), already a very large graph. |

## Falsifiable (27)

| # | Counterexample target | At-home assessment |
|---:|---|---|
| [23](https://www.erdosproblems.com/23) | Triangle-free graph far from bipartite | Medium; extremal graph generation plus MaxCut certificates. |
| [64](https://www.erdosproblems.com/64) | Minimum-degree-3 graph with no cycle of length \(2^k\), \(k\ge2\) | **High-priority computational target**; discrete, exact, graph-isomorphism reduction available. |
| [97](https://www.erdosproblems.com/97) | Convex polygon violating an equidistance property | Low; continuous geometry and exact-distance degeneracies. |
| [106](https://www.erdosproblems.com/106) | Square-packing configuration beating the conjectured value | Low; continuous optimization plus proof of strict improvement. |
| [107](https://www.erdosproblems.com/107) | Counterexample to the Erdős–Szekeres exact value | Very low; famous and deep. |
| [114](https://www.erdosproblems.com/114) | Polynomial lemniscate longer than \(z^n-1\) | Low/medium; numerical discovery is possible, rigorous length certification is hard. |
| [128](https://www.erdosproblems.com/128) | Triangle-free graph satisfying a hereditary density condition | Medium; SAT/MILP-friendly but universal subset constraint is costly. |
| [167](https://www.erdosproblems.com/167) | Graph violating a triangle-packing/covering inequality | Medium/high; exact integer optimization makes a strong certificate pipeline. |
| [242](https://www.erdosproblems.com/242) | Counterexample to the Erdős–Straus conjecture | Very low; huge verified ranges and a famous long-standing problem. |
| [287](https://www.erdosproblems.com/287) | Unit-fraction expansion of 1 with every denominator gap at most 2 | **High-priority symbolic/enumerative target**; exact rational verifier and strong local structure. |
| [375](https://www.erdosproblems.com/375) | Composite interval with no distinct-prime assignment | **High-priority computational target**; reduces each interval to a bipartite matching certificate. |
| [398](https://www.erdosproblems.com/398) | New solution of \(n!=x^2-1\) | Very low; Brocard–Ramanujan type Diophantine difficulty. |
| [458](https://www.erdosproblems.com/458) | Failure of an LCM inequality at some prime index | **High-priority baseline**; cheap exact streaming check, though discovery odds may be low. |
| [488](https://www.erdosproblems.com/488) | Finite divisor set violating a density-ratio bound | Medium/high; discrete search with exact counting and compact certificates. |
| [548](https://www.erdosproblems.com/548) | Counterexample to the Erdős–Sós tree embedding conjecture | Very low; major theorem has an announced proof and ordinary validation must not rely on it casually. |
| [583](https://www.erdosproblems.com/583) | Connected graph needing too many edge-disjoint paths | Medium; exact decomposition verifier, useful graph-enumeration benchmark. |
| [617](https://www.erdosproblems.com/617) | \(r\)-coloring of \(K_{r^2+1}\) with every \(K_{r+1}\) seeing every color | **High-priority SAT target**, beginning with small \(r\); a model is a concise counterexample certificate. |
| [628](https://www.erdosproblems.com/628) | Counterexample to the Erdős–Lovász Tihany conjecture | Very low; famous structural graph conjecture. |
| [699](https://www.erdosproblems.com/699) | Triple \((n,i,j)\) violating a prime-divisor condition on binomial coefficients | **Reproduction baseline pending audit**; tiny exact verifier, but the live page now records two claimed proofs and a GPT-5.6-assisted partial result. |
| [723](https://www.erdosproblems.com/723) | Non-prime-power finite projective plane | Very low; famous and computationally formidable. |
| [743](https://www.erdosproblems.com/743) | Tree collection that cannot pack into \(K_n\) | Low; major tree-packing conjecture. |
| [779](https://www.erdosproblems.com/779) | Primorial interval with no prime \(p\) making \(P+p\) prime | **High-priority baseline**; exact and parallelizable, although primality costs grow rapidly. |
| [982](https://www.erdosproblems.com/982) | Convex point set with too few distances from every vertex | Low; continuous/exact geometry. |
| [993](https://www.erdosproblems.com/993) | Tree with non-unimodal independence polynomial | **Distributed/structural target**; a 2026 preprint reports exhaustive verification through 29 vertices, making the first unchecked full order far beyond a casual at-home enumeration. |
| [1020](https://www.erdosproblems.com/1020) | Counterexample to the Erdős matching conjecture | Very low in general; bounded parameter cases can benchmark hypergraph search. |
| [1041](https://www.erdosproblems.com/1041) | Polynomial whose sublevel set violates a short-path claim | Low; continuous topology and rigorous certification are hard. |
| [1082](https://www.erdosproblems.com/1082) | Point set violating a distinct-distance lower bound | Low; exact geometric search and degeneracy control. |

## Recommended finite-search funnel

**Heuristic ranking for actual discovery:** 647, 375, 617, 287, 993, 699,
835, 64, 458, 488, 779.

**Heuristic ranking for harness validation even without discovery:** 699
(blinded reproduction only), 458, 375, 647, 617, 993 (known small-order
reproduction only). Each admits an independent, compact, exact verifier and a
clear record of negative computational evidence.

Before any run, the chosen page and every cited cutoff/result must be checked
against the original paper. Database status alone cannot carry admission.
