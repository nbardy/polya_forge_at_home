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
without an oracle. The builder must submit the token as the exact EDN
certificate `artifacts/endpoint.edn`:

```clojure
{:format-version 1
 :benchmark-id "blinded-endpoint-benchmark"
 :candidate-token "PF-dddd"}
```

No additional keys are allowed. Admission requires the fixed launcher to hash
that structured token exactly and enumerate the entire 10,000-token domain to
confirm that the digest has exactly one preimage. Free-text mentions do not
count as submissions.

This benchmark scores only the complete endpoint. A prefix match, a partially
searched range, or a smaller distance between hashes scores zero.
