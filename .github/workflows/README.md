# CI/CD Workflows

This project uses **GitHub Actions** for continuous integration (automatic on every push/PR) and **manually triggered** releases (no automatic publishing).

---

## CI — runs automatically

### `ci.yml`

**Triggers:** every push to `main`, every pull request targeting `main`, and manual re-run.

**What it does:**

1. Runs unit tests for each of the 5 implementations *in parallel*:
   - `templane-spec` (Python reference) — pytest
   - `templane-ts` — `npm test`
   - `templane-python` — pytest
   - `templane-java` — `./gradlew build`
   - `templane-go` — `go test ./...`
2. Builds the `templane-conform` CLI (Node.js) and uploads it as an artifact.
3. After all five unit-test jobs pass, runs the **5-way cross-implementation conformance matrix** — all 5 conform adapters × 32 fixtures = **160 expected passes**.

A PR cannot merge unless every job is green, including the conformance gate.

**No secrets required.**

---

## CD — manual trigger only

Every release workflow uses `workflow_dispatch` and **never** runs on push, tag, or schedule. You must go to **Actions → [workflow name] → Run workflow**, provide the version, and click "Run workflow" to publish.

Each workflow also creates a git tag of the form `<library>/v<version>` and a GitHub release with build artifacts attached — regardless of whether you publish to the upstream registry. So even with no secrets configured, you get downloadable artifacts from the release page.

### `release-templane-ts.yml` → npm

| Input            | Purpose                                            |
|------------------|----------------------------------------------------|
| `version`        | e.g. `0.1.0`                                       |
| `dist_tag`       | `latest` / `beta` / `next` / `alpha`               |
| `publish_to_npm` | uncheck for dry-run (no upload, still tags/releases) |

**Secrets:**
- `NPM_TOKEN` — npm automation token with publish rights on `templane-ts`.

### `release-templane-conform.yml` → npm

Publishes the Node.js CLI `templane-conform` for use via `npx`.

| Input            | Purpose                          |
|------------------|----------------------------------|
| `version`        | e.g. `0.1.0`                     |
| `dist_tag`       | `latest` / `beta` / `next`       |
| `publish_to_npm` | dry-run toggle                   |

**Secrets:**
- `NPM_TOKEN` — same token as above (or a separate one scoped to this package).

### `release-templane-python.yml` → PyPI

| Input             | Purpose             |
|-------------------|---------------------|
| `version`         | e.g. `0.1.0`        |
| `publish_to_pypi` | dry-run toggle      |

**Preferred auth: PyPI Trusted Publishing (OIDC, no secrets).**

Setup:
1. Upload the first release manually (one-time), or use PyPI's "pending publisher" flow for a brand-new project.
2. On pypi.org → Account → Publishing → Add publisher.
3. Fill in: GitHub owner, repo name, workflow filename `release-templane-python.yml`, environment `pypi`.
4. No token needed after that.

**Fallback: API token** — set repo secret `PYPI_TOKEN`. The workflow uses it if OIDC isn't configured.

### `release-templane-java.yml` → one of three targets

| Input            | Purpose                                                               |
|------------------|-----------------------------------------------------------------------|
| `version`        | e.g. `0.1.0`                                                          |
| `publish_target` | `artifacts-only` (default, safe), `github-packages`, or `maven-central` |

**`artifacts-only`** (default) — builds all JARs, attaches them to the GitHub release. No registry upload. No secrets needed.

**`github-packages`** — publishes to GitHub Packages Maven registry. Uses the auto-provisioned `GITHUB_TOKEN`. Requires adding the GitHub Packages repo to `templane-java/build.gradle.kts` publishing config:

```kotlin
publishing {
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/OWNER/REPO")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}
```

**`maven-central`** — publishes to Maven Central via Sonatype OSSRH. Requires significant pre-setup (the first time):

1. Create a Sonatype JIRA account and stage the `dev.tsp` coordinate group.
2. Generate a GPG signing key, publish the public key to a keyserver.
3. Configure `templane-java/build.gradle.kts` with the `maven-publish`, `signing`, and ideally `io.github.gradle-nexus.publish-plugin` plugins.
4. Set repository secrets:
   - `OSSRH_USERNAME` — Sonatype account
   - `OSSRH_PASSWORD` — Sonatype token
   - `SIGNING_KEY` — armored GPG private key
   - `SIGNING_PASSWORD` — passphrase for the GPG key

Until the Gradle build is wired up for Maven Central, stick with `artifacts-only` and manually upload via Sonatype's web UI for v0.1.0.

### `release-templane-go.yml` → pkg.go.dev

Go modules are "published" by tagging. This workflow:
1. Runs tests
2. Cross-compiles the `conform-adapter` binary for 4 platforms (linux/darwin × amd64/arm64)
3. Creates tag `templane-go/v<version>`
4. Attaches binaries to the GitHub release
5. Warms the `proxy.golang.org` cache so `go get` finds the new version immediately

| Input             | Purpose                    |
|-------------------|----------------------------|
| `version`         | e.g. `0.1.0`               |
| `trigger_goproxy` | warm the Go proxy (recommended) |

**No secrets required** (uses the provided `GITHUB_TOKEN`).

Consumers install with:

```bash
go get github.com/OWNER/REPO/templane-go@v0.1.0
```

---

## Summary of triggers

| Workflow                             | Trigger         | Secrets needed                   |
|--------------------------------------|-----------------|----------------------------------|
| `ci.yml`                             | push/PR/manual  | none                             |
| `release-templane-ts.yml`            | manual only     | `NPM_TOKEN`                      |
| `release-templane-conform.yml`       | manual only     | `NPM_TOKEN`                      |
| `release-templane-python.yml`        | manual only     | (OIDC preferred) or `PYPI_TOKEN` |
| `release-templane-java.yml`          | manual only     | depends on target (see above)    |
| `release-templane-go.yml`            | manual only     | none                             |

**Important:** None of the release workflows run automatically. Even pushing a tag like `templane-ts/v0.2.0` will NOT trigger a release. You must explicitly go to the Actions tab and click "Run workflow."

---

## Typical release flow

1. Merge work to `main`. CI runs automatically — wait for it to be fully green.
2. Decide which library to release.
3. Go to **Actions → Release [library] (manual) → Run workflow**.
4. Input the version (e.g. `0.1.1`) and any other options.
5. Leave "publish to registry" checked (or uncheck for dry run).
6. Click "Run workflow."
7. The workflow creates the tag, release, and publishes (if registry auth is configured).

## Dry-run first

For the first release of any library, run the workflow with `publish_to_*: false`. That way:
- The tag is created (verifying the versioning works)
- The GitHub release is created with artifacts (so you can inspect them)
- Nothing is pushed to the external registry

Only after you've inspected the artifacts should you re-run with the publish flag enabled.

## Rolling back a bad release

- **git tag**: `git push --delete origin templane-ts/v0.1.1` + `git tag -d templane-ts/v0.1.1`
- **GitHub release**: delete via the GitHub UI or `gh release delete templane-ts/v0.1.1`
- **npm**: cannot unpublish after 72h; deprecate with `npm deprecate templane-ts@0.1.1 "broken, use 0.1.2"`
- **PyPI**: cannot unpublish; upload a fixed `0.1.2`
- **Maven Central**: cannot unpublish; upload a fixed version
- **Go (pkg.go.dev)**: cannot remove a tag once indexed; tag a new fixed version

This is why the `publish_to_*` dry-run flag exists — use it.
