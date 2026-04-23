# 03 — Email with Conditionals ⭐⭐⭐

Demonstrates: **`{{#if}}`, `{{#unless}}`, `{{else}}` chains, list of nested
objects, optional fields affecting output, deep nesting.**

A realistic welcome-email template that branches on plan tier, verification
status, and whether a promo code is present. The kind of template that
would otherwise require 6+ unit tests to cover all branches.

## Files

| File                           | What it is |
|--------------------------------|------------|
| `welcome-email.templane`       | Schema (user object + order list + optional promo) + Handlebars body |
| `data-enterprise.json`         | Verified enterprise user with promo code |
| `data-free-unverified.json`    | Unverified free-tier user, no promo |

## Key schema concept: **list of objects**

```yaml
order_items:
  type: list
  items:
    type: object       # list items are themselves objects
    fields:
      sku:
        type: string
        required: true
      quantity:
        type: number
        required: true
      price:
        type: number
        required: true
  required: true
```

Access via `{{ this.sku }}` inside `{{#each order_items}}`.

## Run — TypeScript + Handlebars

Enterprise-tier user:

```bash
node templane-ts/dist/xt.js render \
  examples/03-email-with-conditionals/welcome-email.templane \
  examples/03-email-with-conditionals/data-enterprise.json
```

Output:
```
Subject: Welcome to Acme, Alice Chen!

Hi Alice Chen,


Welcome to the Enterprise tier. Your account manager will reach out shortly.

Your order:
  - 50 × SEAT-LICENSE-YEAR @ $240
  - 1 × SSO-ADDON @ $2400

Use promo code LAUNCH2026 for 10% off your next purchase.

— The Acme Team
```

Note: the verification notice block was skipped (user IS verified) and the
enterprise `{{else}}` branch was taken.

Free-tier unverified user:

```bash
node templane-ts/dist/xt.js render \
  examples/03-email-with-conditionals/welcome-email.templane \
  examples/03-email-with-conditionals/data-free-unverified.json
```

Output:
```
Subject: Welcome to Acme, Bob!

Hi Bob,


Please verify your email (bob@example.com) by clicking the link we sent you.

You're on the Free plan — you'll see our full feature list inside.

Your order:
  - 1 × FREE-TRIAL @ $0


— The Acme Team
```

Note: verification notice **appears** (user not verified), free-tier branch
taken, promo-code block skipped (field absent, which is legal — it's
optional).

## What to take away

- The type checker understands `list<object<...>>` with full field-by-field
  validation on each list element.
- Optional fields (`promo_code`) can be omitted; the template uses
  `{{#if promo_code}}...{{/if}}` to conditionally render.
- The same schema drives both validation AND render — impossible for
  them to diverge.
- Complex branching logic (plan tier, verification state) is validated at
  template-load time, so runtime never hits a typo like
  `{{ user.is_verifed }}` or `{{#if (eq user.plan "pr0") }}`.

→ Next: [`04-schema-evolution`](../04-schema-evolution/) — how Templane catches
breaking schema changes in CI **before** they ship.
