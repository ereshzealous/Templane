# 02 — User Profile ⭐⭐

Demonstrates: **nested objects, enums, lists, required-vs-optional fields,
multiple errors reported together.**

## Files

| File                  | What it is |
|-----------------------|------------|
| `profile.templane`    | Schema (nested `address` object, `status` enum, `tags` list) + Handlebars body |
| `data-valid.json`     | Fully-populated valid data |
| `data-minimal.json`   | Only required fields (age + postal_code omitted) |
| `data-broken.json`    | Four different error types in one payload |

## Schema highlights

```yaml
status:
  type: enum
  values: [active, inactive, pending, suspended]
  required: true
tags:
  type: list
  items:
    type: string
  required: true
address:
  type: object
  required: true
  fields:
    street:  { type: string, required: true }
    city:    { type: string, required: true }
    country: { type: string, required: true }
    postal_code:
      type: string
      required: false    # optional field
```

## Run — TypeScript + Handlebars

```bash
# Valid
node templane-ts/dist/xt.js render \
  examples/02-user-profile/profile.templane \
  examples/02-user-profile/data-valid.json
```

Output:
```
User: Alice Johnson (age 34)
Status: active
Tags: admin, beta-tester, founding-member
Address:
  123 Maple Street
  Portland, USA 97201
```

Minimal valid:
```bash
node templane-ts/dist/xt.js render \
  examples/02-user-profile/profile.templane \
  examples/02-user-profile/data-minimal.json
```

Output:
```
User: Bob
Status: pending
Tags:
Address:
  1 Way
  Nowhere, US
```

Note: `age` and `postal_code` were omitted but validation succeeded because
both are optional. Handlebars cleanly skipped the `{{#if age}}...{{/if}}`
block and the postal-code fragment.

## The broken data — every error in one pass

```bash
node templane-ts/dist/xt.js check \
  examples/02-user-profile/profile.templane \
  examples/02-user-profile/data-broken.json
```

Exits non-zero with **four** errors in one pass (the type checker collects
everything, never short-circuits):

```
  [type_mismatch]       Field 'age' expected number, got string
  [invalid_enum_value]  Field 'status' value 'acive' not in enum [active, inactive, pending, suspended]
  [type_mismatch]       Field 'tags[1]' expected string, got number
  [missing_required_field]  Required field 'address.country' is missing
  [did_you_mean]        Unknown field 'address.kountry'. Did you mean 'country'?
```

Five specific callouts from one broken file. Compare to the alternative
(hunt down which field silently rendered blank, find the next one, rinse
and repeat).

## What to take away

- **Nested field paths** use dot notation (`address.country`).
- **List element paths** use bracket notation (`tags[1]`).
- **Optional vs required** is a single boolean per field.
- **Enum typos** get pinpoint error messages naming the valid values.
- **Did-you-mean** catches misspellings within Levenshtein distance ≤ 3 —
  `kountry` vs `country` is a distance of 2, so it fires.
- **Every error at once.** You never play whack-a-mole with a template.

→ Next: [`03-email-with-conditionals`](../03-email-with-conditionals/) adds `if/else`
and realistic email structure.
