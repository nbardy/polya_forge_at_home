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

Mathematical memory never crosses packs implicitly. A reusable cross-problem
statement needs its own scope and verification. Running one goal never modifies
another pack.

To add a pack, copy the structure rather than mathematical content, pin an
authoritative source, declare a real external admission method, add an exact
fixture, then run `bb check` and `bb test`.
