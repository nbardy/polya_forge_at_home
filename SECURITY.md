# Security policy

Report vulnerabilities privately to the repository maintainers before public
disclosure.

## Trust boundary

- `bb inspect` parses data and hashes files; it must not execute bundle code.
- Maintainer validation must not execute a contributor's runner, scripts,
  binaries, notebooks, macros, or formalization hooks.
- Engine-code pull requests require separate sandboxed review.
- Symlinks, absolute bundle paths, `..` traversal, non-text artifacts, and
  hash mismatches are rejected.
- Local model processes currently run with Codex workspace-write sandboxing.
  Treat the checkout as readable and keep secrets outside it.

Inspection does not detect secrets. Before publishing, review the text bundle
for environment variables, authentication data, provider tokens, unrelated
home-directory paths, and proprietary source material.
