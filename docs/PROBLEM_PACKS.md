# Problem-pack contract

Each directory under `problems/` is an independent research world. The engine
loads only the files named in its `problem.edn` manifest.

## Required files

```text
problem.edn
target.md
AGENTS.md
memory/KEY_LEARNINGS.md
results/RESULTS_CATALOG.md
results/RETIRED_ROUTES.md
goals/
```

## Required manifest fields

- `:format-version`: currently `1`.
- `:id`: lowercase kebab-case and equal to the directory name.
- `:title`: human-readable title.
- `:status`: `:open`, `:solved`, `:benchmark`, or `:inactive`.
- `:target`: relative path to the canonical target map.
- `:instructions`: relative path to agent instructions.
- `:memory`: relative path to problem-local always-on memory.
- `:catalog`: relative path to the result catalog.
- `:retired-routes`: relative path to the scoped route graveyard.
- `:goals`: relative goals directory.
- `:official-source`: authoritative problem-description URL.
- `:admission`: accepted external verification mechanisms.
- `:budgets`: maximum briefs, workers, invocations, and wall time.

Manifest paths must be relative, remain inside the problem directory, and may
not traverse symlinks.

## Target files

`target.md` freezes the public problem identity and high-level endpoint. It
must state the official source, current status, acceptable terminal outcome,
and what does not count. It should not invent a campaign's first open line.
Every active campaign creates a separate bounded goal that identifies one
literal dependency edge.

## Memory isolation

Problem-local mathematical lessons never enter engine-global context by
default. A cross-problem transfer must become a separately stated theorem or a
verified process lesson, with its own evidence and scope.

Sealed ideation is supported by declaring exclusions in the goal. The manager
must then emit a brief whose snapshot omits the excluded memory files.

## Adding a problem

1. Copy the structure, not the mathematical content, of an existing pack.
2. Write `target.md` from an authoritative source.
3. Declare a real admission mechanism and responsible maintainers.
4. Add one solved or exactly checkable fixture.
5. Run `bb validate <problem-id>` and `bb test`.
6. Submit the problem pack separately from any research result.

