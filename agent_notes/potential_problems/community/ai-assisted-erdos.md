# AI-assisted Erdős problem case studies

Status labels below follow the frozen community wiki (2026-06-30) and current
database snapshot (2026-07-26). They are research leads, not independent
admissions by this repository.

## Reproduction benchmarks

| Problem | Recorded outcome | Why it is useful here |
|---:|---|---|
| [728](https://www.erdosproblems.com/728) | **Solved (Lean).** Recorded as the first autonomous nontrivial AI resolution; the result also settles 729 and 401. | Best end-to-end benchmark: natural-language construction, formal proof, human-readable writeup, and a known failure mode around interpreting the intended nontrivial statement. |
| [729](https://www.erdosproblems.com/729) | **Solved (Lean)** by building on 728. | Tests whether the harness can transfer a frozen proved construction without drifting to a side objective. |
| [397](https://www.erdosproblems.com/397) | **Solved (Lean)** by AI, then found to match a 2012 olympiad solution. | Ideal literature-audit benchmark: mathematical success is not the same as novelty. |
| [333](https://www.erdosproblems.com/333) | AI found a full solution, but comparable 1977 literature was discovered immediately. | Tests mandatory primary-source search and prevents false novelty claims. |
| [897](https://www.erdosproblems.com/897) | AI/Lean solution with a 1981 solution subsequently located. | Another clean “solve versus rediscover” audit benchmark. |
| [1026](https://www.erdosproblems.com/1026) | AI-assisted solution with strengthened conclusion; comparable 2016 literature was found. | Tests collaboration, strengthening, and careful attribution. |
| [1196](https://www.erdosproblems.com/1196) | **Solved** with a recorded novel technique; propagated to 1217, 164, and related conjectures. | Stronger research benchmark, but likely requires frontier models and substantial test-time compute. |
| [90](https://www.erdosproblems.com/90) | **Disproved (Lean)**; a prominent unit-distance conjecture and related problem 92 were resolved. | Ambitious benchmark for construction search plus independent mathematical and formal verification. |

## What recent outcomes actually suggest

1. **Known theorem:** the database contains 1,217 problems, with 556 currently
   marked proved, disproved, or otherwise solved and 608 marked simply open.
2. **Known database fact:** dozens of entries have formal statements or formal
   solutions, making them much better verification targets than informal-only
   problems.
3. **Heuristic:** obscure, sharply stated, finite or construction-based
   problems are currently a better discovery frontier than famous headline
   conjectures.
4. **Known failure pattern:** several apparently novel AI solutions were later
   found in old literature. A source audit must run before novelty is claimed.
5. **Known failure pattern:** variant statements and ambiguous intent can make
   a formally correct result fail to answer the intended problem.
6. **Heuristic:** a solved reproduction set and an open discovery set should be
   kept separate. Reproducing a known proof measures harness reliability, not
   research novelty.
7. **Heuristic:** formalization is an independent gate only when the formal
   statement has first been checked against the intended natural-language
   statement.

## Suggested benchmark ladder

- **Level 0 — exact computation:** independently re-check finite ranges for
  699, 458, or 993 and preserve certificates.
- **Level 1 — known elementary proof:** reproduce 397 or 333 without exposing
  the known proof, then compare and audit novelty.
- **Level 2 — known construction plus Lean:** reproduce 728 and separately
  verify the exact interpretation.
- **Level 3 — bounded open search:** run 647, 375, 617, or 993 with frozen
  search bounds and independent certificate validation.
- **Level 4 — open proof campaign:** only after the lower levels are stable,
  attempt a carefully audited open problem with a formalized statement.

## Sources

- [Community Erdős problem database](https://github.com/teorth/erdosproblems)
- [Frozen AI-contributions wiki](https://github.com/teorth/erdosproblems/wiki/AI-contributions-to-Erd%C5%91s-problems)
- [Resolution of Erdős Problem 728](https://arxiv.org/abs/2601.07421)
- [Aletheia research-agent report](https://arxiv.org/abs/2602.10177)
- [Formal Conjectures repository](https://github.com/google-deepmind/formal-conjectures)

The wiki explicitly warns that it is provisional, not a benchmark, and not a
definitive assessment. This note inherits that caveat.
