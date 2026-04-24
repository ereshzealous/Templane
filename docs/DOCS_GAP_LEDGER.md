# Documentation Gap Ledger

Track documentation issues against the current repository state.

Severity meanings:

- `blocker`: documented workflow is likely to fail for a new user
- `important`: materially confusing, undermines trust, or causes drift
- `polish`: clarity/structure issue that does not usually block execution

| Severity | Area | Current issue | Action |
|---|---|---|---|
| blocker | `xt` CLI docs | Docs present `xt test`, `xt dev`, and `xt build` as sidecar-schema-first, but implementation is still `.hbs` / inline-body oriented for those commands | Either update implementation or keep docs explicit about current scope |
| blocker | Helm / YAML workflows | Adoption and Helm docs describe `xt check ... values-*.yaml`, but `xt` currently parses data as JSON objects only | Keep docs JSON-only until YAML input support exists |
| blocker | TypeScript packaging | Docs read like published npm-package docs, but `templane-ts` is currently marked `private` | Keep source-build workflow as the canonical path until packaging is finalized |
| important | Java packaging | README reads like public Maven/Gradle consumption docs, but repo most clearly supports source build / local publish | Make local build and optional `publishToMavenLocal` the explicit tested path |
| important | Spec/version messaging | Docs refer to multiple spec versions and different status of inline-body form | Choose one version string and one canonical schema-form message everywhere |
| important | `templane-spec` README | Fixture-category summary and reference test-count drifted from repo reality | Keep counts aligned with current tree and tests |
| important | Getting started doc | Mixes repo tour, conceptual explanation, CLI behavior, and first-run instructions in one place | Keep, but tighten around verified local workflows |
| polish | Per-package READMEs | Several assume a more polished external distribution story than the repo currently proves | Add explicit "current repo state" notes until validated |

## Recommended fix order

1. `xt` behavior and input-format truth
2. packaging/install truth
3. spec/version/schema-form consistency
4. doc structure cleanup
