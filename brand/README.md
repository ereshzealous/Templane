# Brand — Templane

Visual identity assets. The final mark is hand-drawn braces `{` and `}` with
a forest-green check inside, on a cream field — evoking a typed template
contract that's been validated.

## Palette

| Token       | Hex       | Use |
|-------------|-----------|-----|
| cream       | `#F4EDE0` | page / card background |
| paper       | `#FBF6EB` | elevated surface |
| ink         | `#2B2A28` | primary type + strokes |
| terracotta  | `#C75B3C` | accent / warnings |
| ochre       | `#D9A441` | highlight / dots |
| forest      | `#4A6B52` | success / conformance |
| brown       | `#6B4423` | muted text |

## Final assets (`final/`)

| File                         | Purpose |
|------------------------------|---------|
| `templane-mark.svg`          | Primary mark, light bg (256×256) |
| `templane-mark-dark.svg`     | Primary mark, dark bg |
| `templane-wordmark.svg`      | Mark + "Templane" + tagline (640×160) |
| `favicon.svg`                | Small-optimized mark (64×64, strokes thickened) |
| `social-card.svg`            | GitHub/OG card (1280×640) |
| `png/`                       | Rasterized exports — mark @ 64/128/256/512, favicon @ 16/32, wordmark, social-card |

## Drafts (`icons/`)

Five directions explored before picking option 5. `preview.html` renders
them side-by-side. Kept as design-provenance; not used anywhere.

## Typography

Serif humanist (Iowan Old Style / Palatino / Georgia). Monospace for
language chips and badges (ui-monospace / SF Mono / Menlo).

## Usage guidance

- **GitHub repo icon / avatar**: `templane-mark.svg` (or `png/templane-mark-256.png`).
- **Docs header / npm / PyPI**: `templane-wordmark.svg`.
- **Favicon**: `favicon.svg` (with `png/favicon-16.png` + `png/favicon-32.png` as PNG fallback).
- **GitHub social preview**: upload `png/social-card.png` in repo Settings → Social preview.
