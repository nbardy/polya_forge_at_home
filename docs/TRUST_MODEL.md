# Trust model

## Trusted

- The checked-out engine version selected by `engine/ACTIVE_VERSION`
- The external static kernel and public schemas on the base branch
- The selected problem pack on the base branch
- The local operator's explicit goal and launch command

## Untrusted or unverified

- Model output
- Contributor bundles
- Builder claims about their own proof
- Recalled theorems without primary-source checks
- Generated runner or prompt changes
- Raw files attached to research contributions

## Controls

- Content hashing before and after a run
- Relative-path and symlink rejection
- Bounded invocations, workers, and time
- Structured output schemas
- Independent verifier for every builder
- Single canonical controller writer
- Stage-boundary checkpoints and explicit resume
- Sanitized export allowlist
- Non-executing public inspection
- Separate activation of recursive engine changes

## Known v0.1 limitations

Codex attempts use the local CLI's `workspace-write` sandbox. This constrains
writes but should not be treated as a complete hostile-code or confidentiality
boundary. Run only from a checkout that contains no secrets. A future public
beta requires an ephemeral capsule/container design and provider-specific
credential brokering.

Formal kernel checking validates a formal statement, not that the statement is
the right translation of the prize problem. Statement fidelity and relevance
still require independent review.

