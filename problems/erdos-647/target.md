# Target: Erdős Problem 647

- **Problem ID:** `erdos-647`
- **Status checked:** open on 2026-07-26
- **Current registry:** https://www.erdosproblems.com/647
- **Original-source keys:** `Er79`, `Er79d`, `Er80` p. 107, `Er92e`,
  and `Er95c`, as indexed by the current registry

## Exact target

Let \(\tau(m)\) be the number of positive divisors of \(m\). Determine whether
there is an integer \(n>24\) such that

\[
  \max_{m<n}\bigl(m+\tau(m)\bigr)\leq n+2.
\]

Equivalently, a proposed witness \(n\) must satisfy
\(\tau(m)\leq n+2-m\) for every integer \(1\leq m<n\).

This is an existential problem. One valid \(n>24\), together with an exact
check of every \(m<n\), resolves the stated target affirmatively. Failure to
find a witness in any finite range does not resolve it.

## Known boundary

The current registry reports that \(n=24\) works and attributes the problem to
Erdős and Selfridge. It records no claimed partial or complete solution in its
comments as of the status-check date. It also records conditional context
about Schinzel's Hypothesis H; a conditional proof does not solve the
unconditional target above.

AI-generated arguments for this problem have previously been reported as
incorrect or conditional. Model agreement, a restricted-window maximum, a
probabilistic heuristic, or an unchecked factorization is not admission.

## Admission

A witness packet must contain:

1. the exact integer \(n\);
2. an exact value of \(\tau(m)\) for every \(1\leq m<n\), or a lossless
   certificate from which each value is independently recoverable;
3. the attained maximum and every equality case;
4. an independent implementation that recomputes the full range using exact
   integer arithmetic; and
5. a human fidelity check against the cited original sources before any public
   claim that the historical Erdős problem has been solved.

Repository `PASS` and publication under `runs/` are evidence-handling events,
not mathematical admission.
