# Security Policy

Thank you for taking the time to report security issues responsibly.

## Supported versions

Templane is early-stage software (pre-1.0 package releases; protocol
spec at 1.0). Security fixes are backported only to the **latest
released minor version** of each package. Older releases are not
maintained.

| Package / artifact            | Currently supported |
|-------------------------------|:-------------------:|
| `templane-ts`                 | latest 0.x          |
| `templane-conform` (CLI)      | latest 0.x          |
| `templane-python`             | latest 0.x          |
| `templane-core` (reference)   | latest 0.x          |
| `templane-java` artifacts     | latest 0.x          |
| `templane-go` module          | latest 0.x          |
| Protocol spec (`SPEC.md`)     | 1.0                 |

## Reporting a vulnerability

**Please do not open a public GitHub issue for security problems.**

Use GitHub's private vulnerability reporting:

➡️ **https://github.com/ereshzealous/Templane/security/advisories/new**

Include, as much as you can:

- Affected component (e.g. `templane-python 0.1.0`, `templane-ts 0.1.0`,
  SPEC §X.Y, the `xt` CLI, etc.)
- A description of the vulnerability and its practical impact
- A minimal reproduction (schema + data + code, or a CLI invocation)
- Any known mitigation or workaround
- Your preferred attribution in the eventual advisory (name, handle,
  or anonymous)

If for some reason the GitHub channel isn't available, email the
maintainer at the address in the repository's git log with the
subject line `[Templane SECURITY]`.

## What to expect

- **Acknowledgement**: within **3 business days** of receiving the
  report.
- **Initial assessment**: within **7 business days** — we confirm the
  issue, ask any follow-up questions, and agree on a severity level
  and a rough fix timeline.
- **Fix + coordinated disclosure**: depends on severity.
  - Critical / High → a patch and public advisory within **30 days**
    of confirmation, ideally sooner.
  - Medium / Low → rolled into the next regular release cycle.
- **Credit**: if you'd like it, you'll be credited in the published
  advisory and in the release notes.

## Scope

In scope — we treat these as security issues:

- Vulnerabilities in any of the Templane implementations
  (`templane-core`, `templane-python`, `templane-ts`, `templane-java`,
  `templane-go`) or their engine bindings.
- Vulnerabilities in the `xt` CLI or `templane-conform` CLI.
- Issues in the specification (`SPEC.md`) that permit an implementation
  to be exploited even while conforming.
- Issues in the conformance fixtures or the conform-adapter protocol
  that mask exploitable behavior.

Out of scope — these are not security issues for *this* project:

- Vulnerabilities in the underlying template engines (Jinja2,
  Handlebars, FreeMarker, Go `text/template`). Report those to their
  own projects.
- Issues in user-authored templates or user-authored schemas. The
  Templane type checker is not a sandbox.
- Server-side template injection (SSTI) that arises from user input
  flowing directly into a template body at runtime. Templane checks
  the **data**; the template body is still code.
- Denial-of-service via pathological schemas or data inputs. We'll
  still accept reports and consider hardening, but these are handled
  as normal bugs unless the impact is unusually severe.

## Safe harbor

We will not pursue legal action against researchers who:

- Make a good-faith effort to avoid privacy violations, destruction of
  data, or interruption of services during their research.
- Give us reasonable time to respond and patch before any public
  disclosure.
- Disclose through the channel above rather than publicly.

Thank you for helping keep Templane users safe.
