<!--
Thanks for contributing to Templane. A few notes to save us both time:

- For non-trivial changes, open an issue or discussion first so we agree
  on the approach before you invest the effort.
- Changes that affect the protocol (SPEC.md, the wire format, the error
  code set, or the fixture suite) require updates to every implementation
  — not just one. A protocol change landing in a single impl will be
  bounced until the others follow.
- Keep commits tight and descriptive. Prefer smaller, reviewable PRs
  over one massive change.
-->

## Summary

<!-- 1–2 sentences describing what this PR does and why. -->

## Type of change

<!-- Pick one or more -->
- [ ] Bug fix (non-breaking)
- [ ] New feature (non-breaking)
- [ ] Breaking change (spec, wire format, or public API)
- [ ] Docs / examples only
- [ ] Internal refactor / test-only

## Changes

<!-- Bulleted list of what changed. -->
- 

## Test plan

<!--
What you ran to verify this works. Be specific. Examples:
  - `(cd templane-python && .venv/bin/pytest)` → 75 passing
  - `(cd templane-ts && npm test)` → 81 passing
  - Full conformance: 5 × 40/40
  - Ran examples/embedded/01-invoice via `xt check`, expected ✓
-->

- [ ] ...

## Conformance impact

<!-- Required if this PR touches any implementation or the spec. -->
- [ ] No impl code changed — N/A
- [ ] Cross-impl conformance (`5 × 40/40`) still passes locally
- [ ] Spec change — fixture suite updated; every impl updated + green

## Related issues

<!-- e.g. "Closes #42", "Part of #18", "See #7 for context" -->
