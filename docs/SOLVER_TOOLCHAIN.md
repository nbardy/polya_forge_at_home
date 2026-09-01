# Solver toolchain

Pólya Forge has an optional, pinned Python 3.12 environment for exact symbolic
work, graph algorithms, SAT/SMT, constraint programming, and numerical
optimization. The managed interpreter and packages install only under ignored
`.forge/python` and `.forge/toolchain`; the task does not modify the system
Python environment.

Install it once and verify every solver family:

```bash
bb solver-install
bb solver-check
```

`solver-install` uses the committed `tools/solver/uv.lock`. `uv` may use its
normal download cache, but the runnable Python 3.12 interpreter, project
packages, and command shims all remain under `.forge/` so the fixed launcher
sandbox can read them.

## Give the tools to research workers

The fixed launcher intentionally inherits only a small environment, including
`PATH`. Start a run or campaign through `solver-exec` so the nested workers see
the pinned environment:

```bash
bb solver-exec bb run problems/<problem-id>/goals/<goal>.edn
bb solver-exec bb campaign 14 problems/<problem-id>/goals/<goal>.edn
```

Inside a build attempt, `python` and `python3` then select the pinned
environment. Workers can import:

- `sympy` for exact algebra, number theory, combinatorics, and symbolic checks;
- `z3` for SAT/SMT constraints and proof-oriented counterexample searches;
- `ortools.sat.python.cp_model` for CP-SAT and finite optimization;
- `scipy.optimize` for numerical and linear optimization;
- `networkx` for graph construction and standard graph algorithms.

Use `bb solver-path` to print the environment's `bin` directory. The smoke
check is also directly executable as
`.forge/toolchain/bin/python tools/solver/smoke.py`.

Solver output is `computational evidence`, not a proof or admission. Preserve
the complete model, exact bounds, solver status, random seeds, and a compact
recomputation recipe. Keep caches, generated instances, databases, and binary
solver state under the attempt's `transient/` directory. Only bounded
text certificates that are needed for the direct endpoint attack belong under
`artifacts/`.
