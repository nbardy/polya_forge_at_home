# Blinded finite endpoint benchmark

Find the unique ASCII token in the finite domain

```text
PF-0000, PF-0001, ..., PF-9999
```

whose exact UTF-8 SHA-256 digest is

```text
a132c0c9c4ec6aae5bacf2ddb9d57a32cf6730aedfd27d44a0b5c28697cde1a6
```

The plaintext solution is intentionally absent from this pack. The predicate
and complete search domain are public, so a builder can derive a candidate
without an oracle. Admission requires a non-builder implementation to hash the
candidate exactly and enumerate the entire 10,000-token domain to confirm that
the digest has exactly one preimage in the stated domain.

This fixture exercises whole-endpoint discovery and verification only. A
prefix match, a partially searched range, or a smaller distance between hashes
is not progress and does not satisfy the endpoint.
