#!/usr/bin/env python3
"""Exercise each pinned solver family with a deterministic tiny certificate."""

from __future__ import annotations

import json
from importlib.metadata import version

import networkx as nx
import scipy.optimize
import sympy
import z3
from ortools.sat.python import cp_model

EXPECTED_VERSIONS = {
    "networkx": "3.6.1",
    "ortools": "9.15.6755",
    "scipy": "1.18.0",
    "sympy": "1.14.0",
    "z3-solver": "4.15.4.0",
}


def check_versions() -> None:
    actual = {package: version(package) for package in EXPECTED_VERSIONS}
    assert actual == EXPECTED_VERSIONS, {"expected": EXPECTED_VERSIONS, "actual": actual}


def check_symbolic_math() -> None:
    x = sympy.symbols("x")
    assert sympy.factor(x**4 - 1) == (x - 1) * (x + 1) * (x**2 + 1)


def check_smt() -> None:
    x, y = z3.Ints("x y")
    solver = z3.Solver()
    solver.add(x > y, y >= x)
    assert solver.check() == z3.unsat


def check_cp_sat() -> None:
    model = cp_model.CpModel()
    x = model.new_int_var(0, 10, "x")
    y = model.new_int_var(0, 10, "y")
    model.add(2 * x + y <= 7)
    model.maximize(3 * x + 2 * y)
    solver = cp_model.CpSolver()
    status = solver.solve(model)
    assert status == cp_model.OPTIMAL
    assert round(solver.objective_value) == 14


def check_continuous_optimization() -> None:
    result = scipy.optimize.linprog(
        c=[-1.0, -1.0],
        A_ub=[[1.0, 2.0], [4.0, 2.0]],
        b_ub=[4.0, 12.0],
        bounds=[(0.0, None), (0.0, None)],
        method="highs",
    )
    assert result.success
    assert abs(result.fun + 10.0 / 3.0) < 1e-9


def check_graph_algorithms() -> None:
    graph = nx.complete_bipartite_graph(3, 4)
    coloring = nx.coloring.greedy_color(graph, strategy="largest_first")
    assert len(set(coloring.values())) == 2


def main() -> None:
    check_versions()
    check_symbolic_math()
    check_smt()
    check_cp_sat()
    check_continuous_optimization()
    check_graph_algorithms()
    print(
        json.dumps(
            {
                "status": "ok",
                "python_math": EXPECTED_VERSIONS,
                "checks": ["symbolic", "smt", "cp-sat", "linear-programming", "graph"],
            },
            sort_keys=True,
        )
    )


if __name__ == "__main__":
    main()
