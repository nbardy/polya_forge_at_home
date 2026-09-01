# Target: Erdős Problem 375

- **Problem ID:** `erdos-375`
- **Status checked:** falsifiable/open on 2026-07-27
- **Current registry:** https://www.erdosproblems.com/375
- **Original-source keys:** `Er72`, `Er73`, and `ErGr80` p. 71, as indexed
  by the current registry

## Exact target

For all integers \(n,k\geq 1\), if
\(n+1,\ldots,n+k\) are all composite, must there be distinct primes
\(p_1,\ldots,p_k\) such that \(p_i\mid n+i\) for every \(1\leq i\leq k\)?

Equivalently, form a bipartite graph whose left vertices are the positions
\(1,\ldots,k\), whose right vertices are the prime divisors appearing in the
interval, and whose edges record divisibility. A counterexample is a composite
interval for which this graph has no matching covering every left vertex.

## Known boundary

The current registry reports the conjecture for \(k\leq 2\), stronger
asymptotic partial results, and verification for every \(k\) when
\(n\leq 1.9\times 10^{10}\), attributed to Laishram and Shorey. It records no
claimed partial or complete solution in its comments as of the status-check
date.

Any discovery claim beyond that reported boundary still requires checking the
Laishram--Shorey paper and the original Erdős sources. Repeating a covered
range is a reproduction result only.

## Admission

A counterexample packet must contain:

1. exact integers \(n\) and \(k\), with \(n>19000000000\);
2. exact factorizations of every \(n+i\), proving all are composite;
3. the complete divisibility bipartite graph;
4. a checkable Hall obstruction, or an independently certified maximum
   matching of size less than \(k\);
5. an independent implementation that reconstructs all factorizations and the
   graph and verifies the obstruction; and
6. human fidelity and literature checks before any public novelty claim.

Finite counterexample-free searches are computational evidence only.
Repository `PASS` is not external mathematical admission.
