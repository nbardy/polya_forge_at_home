# Recommended first campaigns

This is a campaign shortlist, not a claim that any target is easy.

## Tier A: prove the system on exact, bounded work

### 1. Erdős 699 — binomial-coefficient divisor search (audit before use)

- **Claim status:** conjecture; falsifiable by one finite triple \((n,i,j)\).
- **Live-status warning (2026-07-26):** the page now records two claimed proofs
  and a GPT-5.6-assisted partial result. Do not launch or advertise this as an
  untouched open target until those claims and the latest checked range have
  been audited.
- **Objective:** either find and independently verify a counterexample within a
  frozen range, or produce audited computational evidence for that range.
- **Why first:** a tiny exact predicate, cheap parallel search, natural
  number-theoretic pruning, and compact certificates.
- **Gate:** an independent implementation recomputes both binomial
  coefficients, their gcd, and all relevant prime divisors.
- **First missing implication:** after the literature audit, the first
  unverified \(n\) beyond the best published/computed range satisfies the
  required prime-divisor condition for every admissible \(i,j\).
- **Budget suggestion:** begin with one hour and a fixed maximum \(n\); scale
  only if profiling shows meaningful unexplored coverage.

### 2. Erdős 993 — unimodality of tree independence polynomials

- **Claim status:** conjecture; falsifiable by one finite tree.
- **Objective:** enumerate non-isomorphic trees through a frozen order and
  search for a non-unimodal exact coefficient sequence.
- **Why second:** exercises graph generation, dynamic programming, symmetry
  reduction, and compact witness verification.
- **Gate:** a separate recurrence-based coefficient calculator plus a canonical
  graph encoding.
- **Live-status warning (2026-07-26):** a 2026 preprint reports exhaustive
  unimodality verification for all 8,691,747,673 trees on at most 29 vertices.
  Repeating smaller orders is a reproduction benchmark, not open progress.
- **First missing implication:** every tree of order 30 has a unimodal
  independence-set sequence, unless a later primary result extends the checked
  range.
- **Budget suggestion:** freeze a vertex range based on the best audited prior
  computation; do not merely repeat known coverage.

### 3. Erdős 375 — distinct-prime assignment on composite intervals

- **Claim status:** conjecture; falsifiable by one composite interval.
- **Objective:** find an interval whose divisibility bipartite graph has no
  matching covering all positions.
- **Why third:** turns the mathematical predicate into exact matching while
  retaining number-theoretic structure.
- **Gate:** independently factor the interval and verify a Hall obstruction or
  matching failure certificate.
- **First missing implication:** every composite interval in the next frozen
  search region admits a system of distinct prime divisors.

## Tier B: genuine open witness/construction work

### 4. Erdős 647 — divisor-function witness

- **Claim status:** unresolved existential conjecture; verifiable by one \(n\).
- **Objective:** find \(n>24\) with
  \(\max_{m<n}(m+\tau(m))\le n+2\).
- **Why:** exact one-dimensional search, simple independent verification,
  formalized statement, and strong opportunities for pruning.
- **Risk:** the absence of a witness in large searches says little about
  nonexistence.
- **Gate:** independent factorization/divisor-count implementation and direct
  maximum recomputation over every \(m<n\).

### 5. Erdős 617 — Ramsey coloring counterexample

- **Claim status:** conjecture; falsifiable by a finite colored complete graph.
- **Objective:** SAT-search the smallest unaudited parameter \(r\) for a
  coloring in which every induced \(K_{r+1}\) contains every color.
- **Why:** model extraction produces a compact exact certificate and naturally
  supports solver diversity.
- **Gate:** a solver-independent checker enumerates all \(K_{r+1}\) subsets.
- **Risk:** symmetry breaking and proof-of-unsatisfiability validation are
  substantial engineering tasks.

### 6. Erdős 835 — Johnson-graph coloring witness

- **Claim status:** unresolved existential conjecture; verifiable by a finite
  coloring.
- **Objective:** seek a \(k+1\)-coloring of \(J(2k,k)\) for an admissible
  \(k=p-1\), starting only after auditing the known exclusions.
- **Why:** formalized statement, exact certificate, and a clean bridge between
  constructive reasoning and SAT/CP search.
- **Risk:** the first apparent candidate \(k=10\) has \(\binom{20}{10}=184756\)
  vertices, so naive encoding is not home-tractable. Structural mathematics
  must precede brute force.

## Tier C: benchmark known solutions before ambitious discovery

Run blinded reproductions of Erdős 397, 333, and 728. Score separately:

- correctness against the exact intended statement;
- independent verification;
- literature novelty audit;
- ability to stop when the proof is already known;
- preservation of failed approaches.

These runs validate the harness but do not count as new mathematical progress.

## Defer

- Erdős–Straus (242), Erdős–Szekeres exact value (107), finite projective plane
  prime-power conjecture (723), Erdős matching (1020), and other famous
  high-attention targets.
- Continuous geometry targets without an interval/exact certificate pipeline.
- “Decidable” entries whose remaining bound is ineffective or unmanageably
  large.

## Immediate recommendation

Start with two separate problem packs:

1. **647 exact witness search** as the first open campaign with a compact
   independent verifier.
2. **699 blinded bounded reproduction** as the cheapest harness test, only
   after freezing a range already covered by a source; keep it explicitly
   separate from discovery.

Treat **993** as a structural proof campaign or a large distributed order-30
search, not an ordinary at-home enumeration. Only after the first two campaign
types preserve exact bounds, negative evidence, raw logs, and independent
verification should the system spend serious compute on 835 or a distributed
993 campaign.
