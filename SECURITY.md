# Security policy

Report vulnerabilities privately to the repository maintainers before public
disclosure.

## Trust boundary

- `bb inspect` parses data and hashes files; it must not execute bundle code.
- Public CI must not execute a contributor's runner, scripts, binaries,
  notebooks, macros, or formalization hooks.
- Engine-code pull requests require separate sandboxed review.
- Symlinks, absolute bundle paths, `..` traversal, secrets, credentials, and
  oversized artifacts are rejected.
- Local model processes currently run with Codex workspace-write sandboxing.
  Treat the checkout as readable and keep secrets outside it.

Do not publish raw environment variables, authentication files, provider
tokens, unrelated home-directory paths, or proprietary source material.

