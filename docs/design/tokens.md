# Design tokens

The visual baseline is the “vault modernism” system in [SPEC §7](../../specs/SPEC.md#7-frontend). The palette and typeface names below are the complete source palette/type choices; the named `on-*` text colors and state treatments are derived from those choices, not new brand colors.

## Color

| Token | Value | Use |
|---|---|---|
| `background` | `#0F1117` | App canvas; dark text on colored action fills |
| `surface` | `#1A1D27` | Cards, fields, modal, table rows |
| `border` | `#2A2D3E` | Dividers, outlines, field borders |
| `accent` | `#4F8EF7` | Primary actions, links, focus indicator, deposit |
| `success` | `#34D399` | Positive balance/mutation status |
| `danger` | `#F87171` | Validation and mutation errors, withdrawal |
| `text` | `#E8EAF0` | Primary text |
| `muted` | `#8B8FA8` | Secondary labels, hints, timestamps |
| `on-accent` | `#0F1117` | Derived foreground on accent fill |
| `on-success` | `#0F1117` | Derived foreground on success fill |
| `on-danger` | `#0F1117` | Derived foreground on danger fill |

Use the dark `on-*` tokens on filled buttons/badges. Pale `text` is intentionally **not** used on `accent` fills: its contrast is only 2.67:1. State treatment stays within the palette: hover uses the same semantic color with an underline, brighter border, or subtle elevation; it does not introduce another color. Disabled controls use `surface`/`background`, `muted` labels, and no colored fill. The disabled control remains distinguishable by shape and disabled semantics, not color alone.

### Contrast audit

Ratios use the WCAG relative-luminance formula, rounded to two decimals. Normal text target: **4.5:1**; large text (at least 24 CSS px regular or 18.66 CSS px bold): **3:1**. The design targets 4.5:1 for all text, including large balance text. The following is the **complete approved text/background pairing allowlist**; use only these combinations for rendered text.

| Foreground text token | Approved `background` `#0F1117` | Approved `surface` `#1A1D27` |
|---|---:|---:|
| `text` `#E8EAF0` | 15.69:1 Pass | 13.98:1 Pass |
| `muted` `#8B8FA8` | 5.92:1 Pass | 5.28:1 Pass |
| `accent` `#4F8EF7` (text/link) | 5.88:1 Pass | 5.24:1 Pass |
| `success` `#34D399` (status text) | 9.82:1 Pass | 8.75:1 Pass |
| `danger` `#F87171` (error text) | 6.82:1 Pass | 6.08:1 Pass |

| Approved foreground on filled background | Ratio |
|---|---:|
| `on-accent` / `background` `#0F1117` on `accent` `#4F8EF7` | 5.88:1 Pass |
| `on-success` / `background` `#0F1117` on `success` `#34D399` | 9.82:1 Pass |
| `on-danger` / `background` `#0F1117` on `danger` `#F87171` | 6.82:1 Pass |

**Prohibited text pairings:** any foreground/background combination not listed above, including any text on `border` `#2A2D3E`; pale `text` on any colored fill (`accent`, `success`, or `danger`); and `muted` or `accent` text on `border`. In particular, pale `text`/“white” on `accent` is 2.67:1 and fails even the large-text threshold. `border` is for non-text outlines/dividers only; colored fills may be decorative or use only their explicitly listed dark `on-*` foreground. Do not infer approval from a dash or an unlisted pairing.

Disabled text is exempt from WCAG text contrast requirements, but use the approved `muted` on `surface` pairing (5.28:1) wherever possible. Do not lower text opacity, which would invalidate these ratios. Decorative color/state treatments are not text: do not place labels in them.

## Typography

| Token | Specification |
|---|---|
| UI family | Inter |
| UI weights | 400 body/labels, 500 controls/emphasis, 700 headings, 800 balance |
| Money and IDs | JetBrains Mono; account IDs and monetary values |
| Balance numerals | Tabular numerals, 700/800, responsive large size |
| Body | 16px / 1.5 line height |
| Small supporting text | 14px minimum / 1.4 line height |
| Page heading | 28px desktop, 24px mobile; 700 |
| Balance | 40px desktop, 32px mobile; 700/800 |

Use the specified type families and weights only. Do not use color alone to convey transaction type or error status; pair color with a text label/icon.

## Spacing and shape

Spacing uses a 4px base: `space-1` 4px, `space-2` 8px, `space-3` 12px, `space-4` 16px, `space-6` 24px, `space-8` 32px, `space-12` 48px, `space-16` 64px. Use 16px card padding on narrow screens and 24px from tablet upward; section gaps are 24px mobile / 32px desktop.

| Token | Value | Use |
|---|---:|---|
| `radius-sm` | 4px | Fields, compact status badges |
| `radius-md` | 8px | Buttons, small cards |
| `radius-lg` | 12px | Main cards and modal |
| `radius-xl` | 16px | Large vault/balance panel |
| `radius-pill` | 999px | Circular icon controls and pills |

## Motion

| Token | Value | Use |
|---|---|---|
| `motion-fast` | 120ms ease-out | Hover/focus decoration |
| `motion-standard` | 160ms ease-out | Button and field state transitions |
| `motion-modal` | 180ms ease-out | Modal opacity and 4px vertical settle |
| `balance-shimmer` | 1800ms, one linear sweep | First successful balance appearance |

Balance shimmer: apply a text-clipped horizontal gradient with `background-size: 200% 100%`, moving once from left to right. Exact stops: `0% transparent`, `32% #4F8EF7`, `50% #E8EAF0`, `68% #4F8EF7`, `100% transparent`; duration **1800ms**, linear, no repeat. The underlying text color is `text`, so transparent portions retain readable text. After the sweep, render as static `text`.

With `prefers-reduced-motion: reduce`, do not animate or transition the balance, modal, button, or field. Show the balance immediately in static `text`; show the modal immediately at its final position/opacity. Hover/focus feedback remains visible as a static underline, border, or outline. Pending state uses a static label (“Saving…”), not a spinner.
