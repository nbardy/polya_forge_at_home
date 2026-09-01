# Problem packs

Each `problems/<id>/` directory is an isolated mathematical research world:

```text
problem.edn
target.md
AGENTS.md
goals/*.edn
memory/KEY_LEARNINGS.md
results/RESULTS_CATALOG.md
results/RETIRED_ROUTES.md
```

Those paths are conventions, not configurable aliases.

`problem.edn` contains only identity, status, official source, and external
admission mechanisms. A goal contains the bounded objective, endpoint edge,
first open line, inputs, exclusions, completion/kill criteria, and run budget.
Its objective, endpoint edge, and first open line define one direct attack and
are copied unchanged into every brief. Partial byproducts may be remembered
only as non-completing salvage.

Invoke the shared controller with the goal path alone. The goal's `:problem`
field selects its pack:

```bash
bb campaign 10 problems/<problem-id>/goals/<goal>.edn
```

Mathematical memory never crosses packs implicitly. A reusable cross-problem
statement needs its own scope and verification. Running one goal never modifies
another pack.

Completed research can be made visible without publishing raw controller state:

```bash
bb publish <run-id>
```

The fixed launcher validates the research export and writes it to
`runs/<problem-id>/<run-uuid>/`. The ignored `.forge/` tree remains the
resumable runtime and contains data that is intentionally not public.

To add a pack, copy the structure rather than mathematical content, pin an
authoritative source, and declare a real external admission method. Controller
regression data lives once under `test/fixtures/`; do not duplicate synthetic
fixtures inside mathematical problem packs. Then run `bb check` and `bb test`.

## Harness selection benchmarks

Harness mutation and harness selection are separate events. Compatibility,
authority, and lifecycle checks may install one probationary challenger, but
they do not change the active champion or show that the challenger is better.
The launcher evaluates champion and challenger on the same launcher-trusted
blinded endpoint, goal hash, and budget, restoring the same benchmark-memory
baseline between arms. Promote the challenger only when it produces strictly
more independently admitted whole endpoints; partial candidates, smaller
obstruction counts, additional packets, and larger negative frontiers may
steer a run but cannot enter selection or break a tie.

There are three benchmark boundaries:

- Synthetic controller fixtures live under `test/fixtures/problems/` and are
  never production research inputs.
- `problems/blinded-endpoint-benchmark/` is the one production harness-selection
  pack. Its predicate and finite domain are public, its plaintext endpoint is
  absent, and the fixed launcher performs exhaustive admission. With exactly
  one open challenger, run it with
  `bb benchmark problems/blinded-endpoint-benchmark/goals/find-token.edn`.
- `problems/benchmark-*/` contains source-withheld historical reproductions.
  These are not cryptographically blinded and the fixed launcher does not admit
  or score them. Run them as ordinary research goals, for example with
  `bb solver-exec bb run problems/<historical-benchmark>/goals/solve.edn`.
  A model `PASS` or endpoint candidate remains pending until every external
  mechanism named in that pack's `problem.edn` has been performed by an
  independent source-holding auditor.

All three kinds test whole-endpoint behavior rather than recognition of an
answer embedded in a prompt. None is a claim of new mathematical progress.
