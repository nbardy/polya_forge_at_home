# Contributing

There are two contribution types. Do not combine them in one pull request.

## Research contribution

1. Choose a problem pack and create a bounded goal.
2. Run Forge locally.
3. Export the terminal run with `bb export <run-id>`.
4. Inspect it with `bb inspect <bundle-path>`.
5. Open a pull request containing the sanitized bundle and a concise statement
   of the claimed dependency-graph change.

A research contribution may propose a result, correction, refutation,
reproduction, source audit, formalization, or memory candidate. It may not
change files under `engine/`.

## Engine contribution

Engine changes require a stated hypothesis, evidence, expected benefit,
regression risk, and cheapest test. Run `bb check` and `bb test`. Engine
changes may not promote mathematical claims produced during their development.

## Review outcomes

- `ACCEPTED_SEARCHABLE`: retained as useful evidence, not admitted truth.
- `ADMITTED`: passed the problem pack's independent external gate.
- `REPAIR`: a precise defect is identified and a versioned repair may follow.
- `RETIRED`: an exact route class is killed with a reopening condition.
- `QUARANTINED`: provenance, privacy, schema, or trust failure prevents reuse.
- `REJECTED`: no useful, valid, or in-scope contribution remains.

All contributors retain attribution through bundle IDs, hashes, and Git
history. Prize attribution is governed by the relevant external institution,
not by this repository.
