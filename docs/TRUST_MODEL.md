# Trust model

Trusted during a local run:

- the checked-out and hashed `engine/` tree;
- the selected problem pack and active goal;
- the local Codex executable and operating system.

Not trusted as mathematical authority:

- builders, verifiers, planners, memory proposals, or harness reflections;
- repository maintainers acting without the pack's external admission method;
- contributor bundles before static and substantive review.

The controller isolates each model call in its own writable directory, freezes
inputs, preserves call failures, and links later work to hashed packets.
These controls provide provenance, not proof.

Bundle inspection is deliberately static. It checks text-only paths, file
sets, hashes, and symlinks; it does not execute contributor code.

The local alpha is not a hostile-code sandbox. Engine pull requests and any
future executable artifacts require separate review and sandboxing.
