# 04 — Schema Evolution ⭐⭐⭐⭐

Demonstrates: **the breaking-change detector**. Given two versions of a
schema, produces a list of changes that would break existing callers —
exactly the mechanism you'd wire into CI to block risky PRs.

Recall the story from the root README — a backend engineer renames
`user.email` → `user.email_address` and password-reset emails go out blank
for four days. This is the example that demonstrates catching that PR in
CI **before** it ships.

## Files

| File                         | Role |
|------------------------------|------|
| `v1-schema.yaml`             | Baseline (production) schema |
| `v2-schema-safe.yaml`        | Proposed v2 with only non-breaking changes |
| `v2-schema-breaking.yaml`    | Proposed v2 with multiple breaking changes |
| `detect_changes.py`          | Runnable detector script — exits non-zero on breakage |

## What the detector reports

Four breaking-change categories (from SPEC §8):

| Category              | Condition                                              | Example |
|-----------------------|--------------------------------------------------------|---------|
| `removed_field`       | field in old schema, absent in new                     | `user.email` → gone |
| `required_change`     | field went from optional → required                    | `display_name` was `required: false`, now `required: true` |
| `type_change`         | field type (`kind`) changed                            | `tags` was `list<string>`, now `string` |
| `enum_value_removed`  | enum value in old schema absent from new               | `status` dropped `"pending"` |

Non-breaking: adding a new optional field, adding a new enum value, making
a required field optional.

## Run — safe evolution

```bash
cd examples/04-schema-evolution
python3 detect_changes.py v1-schema.yaml v2-schema-safe.yaml
```

Output:
```
✓ No breaking changes from v1-schema.yaml → v2-schema-safe.yaml
```

Exit code: **0**. CI is happy, the PR merges.

## Run — breaking evolution

```bash
python3 detect_changes.py v1-schema.yaml v2-schema-breaking.yaml
```

Output:
```
✗ 4 breaking change(s) from v1-schema.yaml → v2-schema-breaking.yaml:
  [removed_field]       user.email: StringType → <absent>
  [enum_value_removed]  user.status: pending → <removed>
  [type_change]         tags: ListType → StringType
```

Exit code: **1**. CI blocks the PR.

> Note: the `v2-schema-breaking.yaml` also *adds* a new required field
> (`user.tenant_id`). The spec doesn't classify this as a breaking change
> category — adding requireds is breaking in practice, but not one of
> the four formal categories. A future extension could add it.

## Setup before running

```bash
cd templane-python
uv sync --extra dev
source .venv/bin/activate
# Now from the repo root:
python3 examples/04-schema-evolution/detect_changes.py <old> <new>
```

## Using this in CI

Add a workflow step that runs the detector on the merge base:

```yaml
- name: Check for breaking schema changes
  run: |
    git fetch origin main
    git show origin/main:templates/user.templane > /tmp/old-schema.yaml
    # extract schema from PR head
    awk '/^---$/{exit} {print}' templates/user.templane > /tmp/new-schema.yaml
    python3 scripts/detect_changes.py /tmp/old-schema.yaml /tmp/new-schema.yaml
```

Any breaking change fails the PR check with a clear, categorized
explanation — not a mysterious "production silently degraded" postmortem
three days later.

## What to take away

- Schemas version. Templates version with them. The detector makes the
  diff explicit.
- **Only four breaking categories**, all mechanically detectable. No
  human judgment needed.
- CI integration is ~5 lines of shell.
- You can adopt this retroactively: treat your current schema as v1,
  every future PR as v2, and you've got an evolution gate.

→ Next: [`05-cross-language-parity`](../05-cross-language-parity/) — proof that
the SAME template renders byte-identical output across Python, TS, Java, and Go.
