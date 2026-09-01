# Trust model

Trusted during a local run:

- the checked-out and hashed `engine/` tree;
- the selected problem pack and active goal;
- the local Codex executable and operating system.

Not trusted as mathematical authority:

- builders, verifiers, planners, memory proposals, or harness reflections;
- repository maintainers acting without the pack's external admission method;
- contributor bundles before static and substantive review.

The controller gives each model turn its own attempt directory, freezes inputs,
preserves call failures, and links later work to hashed packets. Builder turns
also pin that directory as their app-server `cwd`, runtime workspace root, and
only model-writable root. The trusted Codex executable still maintains its own
authentication and durable thread store outside the run. These controls provide
provenance, not proof.

Bundle inspection is deliberately static. It checks text-only paths, file
sets, hashes, and symlinks; it does not execute contributor code.

The immutable launcher treats an evolved engine as hostile repository code. A
fixed Codex permission profile gives it read-only source access and exact
writes only to its assigned run, same-problem memory, requested export, and
temporary state. Candidate checks use the same boundary. This does not sandbox
the operating system or the trusted Codex executable, establish mathematical
truth, or make executable contributions safe to merge without review.

Planner, verifier, memory, and reflection calls ignore ambient user
configuration and exec-policy rule files. Builders instead use Codex's
app-server so an exact repair can resume a stored conversation across processes;
the launcher overrides model, effort, approval policy, working/write roots, and
supplies the frozen `AGENTS.md` snapshot as developer instructions. It sets
`project_doc_max_bytes=0` for that app-server process so ambient project or
user `AGENTS.md` files cannot add instructions; other trusted Codex user config
and hooks may still run. A stored
builder thread is untrusted working context, not canonical evidence. Its ID is
recorded in the call and packet, while the frozen prompt, result, verifier, and
artifacts remain the auditable record. Verifiers are always fresh ephemeral
calls and the broker rejects any builder conversation ID attached to them.

Run authority lives outside the engine-writable run tree. The launcher binds a
run ID to one goal hash, problem-memory scope, engine pin, and launcher hash
before launch; resume checks the frozen input and manifest against that binding.
An evolved engine cannot obtain write access to another problem by changing
its own `run.edn`.
