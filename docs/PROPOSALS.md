# Proposal protocol

A proposal is a review request, not a claim of truth or progress. Local work
remains local unless it changes what another rigorous attempt should do.

## Research proposals

Research proposals contain one sanitized immutable bundle at
`runs/<problem-id>/<run-uuid>/`. The bundle UUID is its public merge identity.
Never rewrite an accepted bundle; a correction creates a descendant UUID and
links to the rejected or superseded parent.

Propose when at least one of these is present:

- an independently verified complete endpoint candidate;
- a new proved lemma that directly reduces the frozen first open implication;
- a reproducible computation or counterexample that changes the live route;
- a precise refutation that retires a previously live route; or
- unusually strong salvage that prevents a specific repeated failure.

Do not propose when the bundle only adds prose, repeats known evidence, has no
independent verification, changes the goal, treats model agreement as proof,
or offers a reusable idea without showing how it changes the exact open line.

An accepted partial result remains labeled partial. It may update a
problem-local learning pointer, but it scores zero as endpoint completion and
zero as evidence that the harness improved.

## Harness proposals

Harness proposals use a separate PR and include one receipt under
`harness_history/<proposal-uuid>/proposal.edn`. The receipt must declare:

- proposal UUID and parent engine hash;
- scope: one problem, a named problem family, or all problems;
- reproduced defect or successful behavior being generalized;
- changed engine file and falsifiable hypothesis;
- research evidence UUIDs;
- frozen endpoint benchmark and regression risks; and
- resulting engine hash after review.

Prefer learning from independently verified successes because they show a
complete behavior worth reproducing. Failures may justify a guard or route
retirement, but more failures, tokens, packets, labels, or self-review never
establish harness improvement.

Harness proposals must not include mathematical claims, activate themselves,
or replace the champion on a tie. A learner that is useful only for some
problem classes must declare that scope instead of presenting itself as
universal.

## Research-lead decisions

The research lead records one of four outcomes:

- `accept`: unique, correctly scoped evidence worth preserving;
- `synthesize`: overlapping proposals should produce a new descendant rather
  than merge conflicting or duplicated learning;
- `reject`: unsafe, unsupported, irrelevant, malformed, or duplicate;
- `defer`: potentially useful but missing a named verification or benchmark.

The lead reviews immutable artifacts by pointer. It does not silently edit a
contributor's failed derivation, combine research and harness code in one PR,
execute untrusted bundle content, or imply that a repository merge admits a
mathematical claim.

## Required PR summary

Every proposal states:

1. problem and exact frozen objective;
2. proposal UUID and parent UUIDs, if any;
3. claim statuses and strongest independently verified result;
4. failed steps and unresolved checks;
5. what existing result or learning it duplicates or supersedes;
6. requested outcome: accept, synthesize, defer, or reject; and
7. for harness work, scope and matched benchmark evidence.

