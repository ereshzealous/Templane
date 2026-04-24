# Brand — Templane

The mark is two braces — `{` and `}` — wrapping a capital **T** with an
emerald crossbar. Typed template, bracketed. The palette is ink,
cream, and emerald.

## Palette

| Token      | Hex        | Use |
|------------|------------|-----|
| ink        | `#0A0A0A`  | primary type, mark braces + T-stem on light |
| cream      | `#FAFAF7`  | primary bg on dark; mark on dark surfaces |
| card       | `#F5F1E8`  | warm neutral for cards / docs panels |
| hero-bg    | `#1C1C1E`  | dark hero / splash bg |
| emerald    | `#0F5C3A`  | T-crossbar on light; brand color for panels |
| emerald-hi | `#4ECB8C`  | T-crossbar on dark; success / "conformant" accents |

## Assets

### `svg/` — source of truth

| File | Use |
|---|---|
| `mark-primary.svg` | Default mark. Ink + emerald. Transparent bg. |
| `mark-reverse.svg` | Cream + bright-emerald. For dark surfaces. |
| `mark-mono-ink.svg` | All ink. Print fallback, single-color contexts. |
| `mark-mono-cream.svg` | All cream. Use on a brand-green field. |
| `wordmark-horizontal.svg` | Mark + "templane" side-by-side. |
| `wordmark-stacked.svg` | Mark above "templane". |
| `wordmark-only.svg` | Just the word. |
| `favicon.svg` | Thicker strokes. Render at 16/32/48/64. |
| `hero.svg` | Dark hero with mark + wordmark + tagline + proof line (1280×640). Also the social card. |

### `png/` — rasterized

Mark: 16 · 24 · 32 · 48 · 64 · 128 · 256 · 512 px
Reverse mark: 512 px
Favicon: 16 · 32 px
Wordmark horizontal: full size
Social card (`social-card.png`): 1280 × 640 — upload as the GitHub repo Social preview.

### `source/`

`complete_brand_system.html` — the full designer lockup (all 5 sections:
logo formats, color variants, size scaling, applications, hero). Kept
as the canonical reference.

## Usage

| Surface | Asset |
|---|---|
| GitHub repo avatar | `png/mark-512.png` |
| GitHub Social preview (repo settings) | `png/social-card.png` |
| Favicon | `svg/favicon.svg` (modern browsers) + `png/favicon-16.png` + `png/favicon-32.png` (fallback) |
| README header | `svg/mark-primary.svg` @ 80–120px |
| Docs site header | `svg/wordmark-horizontal.svg` |
| npm / PyPI project logo | `svg/mark-primary.svg` |
| Dark-mode docs | `svg/mark-reverse.svg` |
| CLI terminal splash | `png/mark-24.png` or `mark-32.png` |
| App icon / social card | `png/mark-reverse-512.png` on `#1C1C1E` rounded-square field |

## Typography

- **Display / wordmark**: SF Pro Display → Helvetica Neue → Arial, weight 500, letter-spacing −0.03em
- **Monospace / technical labels**: SF Mono → Menlo → ui-monospace

## Constants the SVGs depend on

- ViewBox: `0 0 100 100` (mark canvas)
- Mark stroke width: 6 at 256px+, 8 at 32px, 9 at 24px, 11 at 16/favicon
- Brace path (left): `M 28 22 C 20 22, 18 30, 18 38 L 18 44 C 18 48, 15 50, 12 50 C 15 50, 18 52, 18 56 L 18 62 C 18 70, 20 78, 28 78`
- T-stem: `rect x="36" y="28" width="6" height="44"`
- T-crossbar: `rect x="42" y="46" width="22" height="8"` (emerald)
