Plan the next bounded research wave for one frozen goal.

Read the snapshot and the index of packets frozen in this run. Emit at most the
stated number of exact, non-duplicative briefs; the fan-out limit is a ceiling,
not a quota. Every later brief must cite packet IDs from that index; IDs in
cross-run memory are evidence pointers, never `parent_packet_ids`. A `PASS`
parent may support a premise; any other parent may be cited only for repair or
falsification.

Persist on the complete endpoint. `REPAIR` is reserved by the verifier for an
endpoint-bearing defect whose correction could change whether the complete
frozen objective is satisfied. If such a frontier packet exists, at least one
next brief must cite it, implement its `smallest_repair`, and resubmit the
complete objective. Continue from its exact candidate and obstruction rather
than restarting or polishing audit metadata. A repaired descendant is not
partial progress and must not weaken completion criteria.

Prefer continuity on one exact obstruction over novelty fan-out. When a parent
contains an otherwise complete candidate with a named endpoint obstruction,
allocate one brief to correcting that obstruction before proposing unrelated
routes. The obstruction may steer search but scores zero as progress or
success. Emit fewer briefs, or an empty array, when no endpoint-relevant repair
is open and no remaining brief can name a failure-derived, falsifiable reason
that its mechanism could close the frozen line. Do not manufacture adjacent
searches merely to fill fan-out, but do not stop or switch routes while a
concrete untried endpoint repair remains.

The goal's objective, endpoint edge, and first open line are immutable. Copy
all three verbatim into every brief. Fan-out only genuinely different direct
strategies against that same first missing implication; express the strategy
through inputs, exclusions, falsifier, and kill criteria.

Never plan a reusable lemma, source audit, formalization, computation, asset,
or memory entry for its independent value. Such material may survive only as
incidental salvage from a direct attack. If no direct attack on the frozen line
remains after all concrete repairs are killed, emit an empty `briefs` array. Do
not assess or mutate the harness.

Return only the structured response required by the supplied schema.

Maximum briefs this wave: {{BRIEF_LIMIT}}

{{SNAPSHOT}}

## Prior audited packet index

{{PACKET_INDEX}}
