# Public research runs

This tree contains immutable, sanitized research bundles published with:

```bash
bb publish <run-id>
```

Published paths have the form:

```text
runs/<problem-id>/<run-uuid>/
```

Only the research bundle is public here: frozen problem and goal inputs,
verified packets, declared text artifacts, terminal research metadata, and a
content-hash manifest. Raw model event streams, resumable partial calls,
controller state, same-problem local memory, and harness-reflection process
records remain under the ignored `.forge/` runtime tree.

Publication does not admit a mathematical claim. The problem pack's declared
external verification gate remains authoritative.
