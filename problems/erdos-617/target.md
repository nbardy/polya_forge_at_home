# Target: Erdős Problem 617

- **Problem ID:** `erdos-617`
- **Status checked:** falsifiable/open on 2026-07-27
- **Current registry:** https://www.erdosproblems.com/617
- **Original-source keys:** `ErGy99` and `Er99`, as indexed by the current
  registry

## Exact target

For \(r\geq3\), if the edges of \(K_{r^2+1}\) are colored with \(r\) colors,
must there exist \(r+1\) vertices whose induced \(K_{r+1}\) omits at least one
color?

A counterexample is an \(r\)-coloring in which every induced
\(K_{r+1}\) contains all \(r\) colors.

## Known boundary

The current registry attributes the conjecture to Erdős and Gyárfás, reports
proofs for \(r=3\) and \(r=4\), and notes that the analogous statement for
\(r=2\) is false. It records no claimed partial or complete solution in its
comments as of the status-check date. The first unknown finite case is
\(r=5\): a 5-coloring of the 325 edges of \(K_{26}\) in which every induced
\(K_6\) contains all five colors.

## Admission

A counterexample packet for \(r=5\) must contain:

1. a canonical lossless encoding of the color of every edge of \(K_{26}\);
2. a checker that enumerates all \(\binom{26}{6}=230230\) six-vertex subsets
   and verifies that all five colors occur on every induced \(K_6\);
3. an independent checker implementation that parses the same certificate and
   repeats the exhaustive test; and
4. human primary-source and literature checks before any public novelty claim.

SAT solver output without a solver-independent full checker is not admission.
Unsatisfiability of one encoding or finite search does not prove the universal
conjecture.
