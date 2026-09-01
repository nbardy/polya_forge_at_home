# Potential problem catalog

This directory is a scouting notebook, not a collection of admitted results or
executable problem packs. A candidate only moves into `problems/` after its
statement, literature status, endpoint, first missing implication, and
verification gate have been frozen.

Snapshot date: 2026-07-26.

## What is here

- `erdos/all.md`: all 1,217 entries in the community Erdős database, with
  status, prize, formalization flag, tags, and a canonical link.
- `erdos/open.md`: the 608 entries classified simply as open.
- `erdos/finite-search.md`: the 43 entries classified as decidable,
  verifiable, or falsifiable, plus a tractability assessment.
- `erdos/solved.md`: all 556 proved, disproved, or otherwise solved entries.
- `community/ai-assisted-erdos.md`: recent AI/community case studies and the
  lessons they provide for benchmark design.
- `adjacent/source-lists.md`: other large sources of candidate conjectures.
- `shortlist/first-campaigns.md`: the recommended first campaigns for Pólya
  Forge at Home.

## Claim labels used here

- **Known theorem / solved / disproved** means the source database currently
  records that status. It is not an independent source audit by this project.
- **Conjecture / open / decidable / verifiable / falsifiable** means the source
  database currently records that status. Open status is provisional until a
  fresh literature audit is complete.
- **Heuristic** labels our assessment of tractability or harness value.
- **Computational evidence** labels finite checks, never a general proof unless
  the frozen problem explicitly reduces to that finite check.
- **Unresolved** means no admitted proof or disproof is known to this notebook.

## Source and reproducibility

The exhaustive lists were generated from
[`teorth/erdosproblems`](https://github.com/teorth/erdosproblems), commit
`f15849873be8cef2b526dafe6cde43b57096ac49` (2026-07-26). That repository calls
`data/problems.yaml` its ground truth. The corresponding website warns that
statuses and statements can be incomplete or ambiguous; every promoted
candidate therefore needs a primary-source and literature audit.

The AI case-study note uses the frozen community wiki at commit
`c8ad4309d20120c67cb97faa86daa1443acee018` (latest data 2026-06-30).

## Promotion rule

A high catalog score is not evidence that a conjecture is true or easy. Before
creating a problem pack:

1. copy the exact source statement and list every ambiguity;
2. audit the original Erdős source and the most recent cited literature;
3. decide whether the objective is proof, disproof, a bounded case, or
   reproduction of a known result;
4. freeze one endpoint edge and its first missing implication;
5. choose an independent verification gate;
6. set finite token, wall-clock, and computation budgets.

No catalog entry is itself a research objective.
