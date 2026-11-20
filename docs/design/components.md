# Component and page specifications

This document implements the page/component scope in [SPEC §7](../../specs/SPEC.md#7-frontend). Desktop is 1024px and wider; mobile is 375px. Between those widths, retain the mobile single-column order until there is room for two usable form columns. Built static assets are served by Dropwizard; client-side page routes must work on direct navigation and refresh ([ADR-004](../adr/004-spa-serving.md)).

## Shared interaction and accessibility

- Use semantic headings, buttons, forms, labels, lists/tables, and dialogs. All controls are keyboard operable; links and buttons have visible focus.
- Focus order follows visual/DOM order, top to bottom and left to right. On Dashboard: account heading/actions → balance card → deposit form → withdrawal form → transaction history/pagination. On Home: account-ID search field → Search → Create account. Modal opening moves focus to its heading or first field; Tab stays inside it, Shift+Tab reverses, Escape/Cancel closes it, and closing returns focus to the Create account trigger.
- Focus ring: 2px solid `accent`, 2px offset against `background`/`surface`. Never remove the native outline without this replacement.
- Inputs have persistent visible labels; placeholders are examples only. Associate inline hint/error text with its field and set invalid state programmatically. On submit, move focus to the first invalid field. Announce a newly presented form error with a polite live region; use an assertive announcement only for a blocking server failure. Do not repeatedly announce unchanged errors.
- Transaction success/pending and refreshed balance are announced in a polite status region. Pending prevents duplicate submission. Keep the form and entered values available if a request fails.
- Contrast uses the approved-pairing allowlist in [tokens.md](tokens.md). Error text uses `danger` on `surface` (6.08:1) or `background` (6.82:1); hints use `muted` only on `surface` (5.28:1) or `background` (5.92:1). Never put text on the `border` token.
- Every visual state has a non-color cue: type label/icon for transaction direction, text for pending/error, and outline/underline for hover/focus.

## State contract

| Component | Default | Hover | Focus | Pending | Error | Empty | Loading |
|---|---|---|---|---|---|---|---|
| **AccountCard** | Name/ID and formatted balance on `surface`; link/action if available | Card link underline or subtle elevation; no content change | Whole-card link gets visible 2px accent ring; never make a non-link card focusable | Static “Refreshing…” status; retain last known balance, mark stale only in text | Show inline “Balance unavailable” and Retry; do not display a fabricated zero | Not applicable: an account card requires account data; on no account, use Home empty prompt instead | Fixed-size skeleton placeholders; announce “Loading account” once; do not shimmer skeletons |
| **TransactionForm** | Labeled amount, optional note, submit; client-side positive amount / max two decimals validation | Button accent treatment/underline; field border changes to accent | Field focus ring; errors remain associated and visible | Optimistic pending state per SPEC: disable duplicate submit, static “Saving…” label; preserve values, announce status; rollback optimistic balance/history on failure | Inline field error or backend `error`; associate and announce; retain input; refocus first invalid field after client validation | Blank amount and note are a valid initial form state, with no error until submit/blur validation rule requires it | Disable submission, keep labels/layout stable, static “Loading…” status; no skeleton over editable form |
| **TransactionHistory** | Newest first; type icon + text, signed amount, timestamp; scrollable region with accessible name | Row background may use `surface`; links underline | Focus ring on pagination/actions; rows are not tab stops unless actionable | Keep existing rows, announce “Updating transactions”; do not replace data with a spinner | Inline retry message; preserve already loaded rows and pagination | “No transactions yet — make your first deposit” with a deposit action/link | Stable-height row placeholders; announce “Loading transactions” once; no shimmer |
| **CreateAccountModal** | Dialog with heading, name/email/optional initial deposit, Create and Cancel | Button/link decoration only | Focus contained in dialog; visible ring; close button labeled | Disable duplicate creation; static “Creating account…” status; retain values | Inline field error or API error (including duplicate email); announce; retain values and focus first invalid field | Initial blank form is the empty state; show required labels, no prefilled account data | If opening needs initialization, show dialog title and static loading text; keep close action available |

“Empty” for form/modal means an untouched blank form; it is not an error. For loading and pending, prefer stable content and text status over moving indicators. These states can coexist (for example, history can be loading with no rows yet); loading takes precedence until the request resolves, then show empty or data.

## AccountCard

Reusable summary for an account: account name, masked/compact email only where useful, account ID in JetBrains Mono with a copy control, and formatted balance. Do not treat an ID as a secret; keep its display readable and allow copy. Keep the balance as a single signed/decimal amount with tabular numerals. On small screens content stacks; on desktop identity and balance can share one row. If the card navigates, make the card a single link and expose copy as a separate adjacent button, not nested interactive controls.

## TransactionForm

One form per operation, with Deposit and Withdraw headings and amount in JetBrains Mono. Optional note is secondary. Deposit is the positive/accent action; withdrawal uses danger text/icon plus a clear “Withdraw” label, but does not use a danger-filled button with light text. Client validation runs before an API call: amount must be greater than zero and have at most two decimal places. Show server `error` inline (including insufficient funds); do not clear fields after success until the updated state is confirmed. Mutation errors roll back the optimistic view and trigger the balance/history refetch specified by the product behavior.

## TransactionHistory

Show newest first and maintain an independently scrollable list at desktop within the available Dashboard column; on mobile it flows naturally with the page. Each row has an explicit “Deposit”/“Withdrawal” label (and other API transaction types when present), timestamp, and amount. Color and sign supplement, never replace, the label. Empty state uses the exact copy: **“No transactions yet — make your first deposit”**. Pagination controls, if present, follow the list in focus order.

## CreateAccountModal

Use a modal dialog over a dimmed `background` overlay. Dialog is `surface`, `radius-lg`, and has a visible title, labeled Name and Email fields, optional Initial deposit, Create account and Cancel controls, and a named close button. Initial deposit follows the same amount validation and max-two-decimal rule. Inline errors are adjacent to the corresponding field; duplicate-email and other backend messages appear as a form-level error. Closing is prohibited while a create request is pending unless the request is safely canceled; otherwise retain the dialog until completion.

## Responsive page wireframes

### Home

```text
MOBILE — 375px                         DESKTOP — 1024px+
+--------------------------------+     +--------------------------------------------------+
| Bank in a Box                  |     | Bank in a Box                         [Create] |
|                                |     |                                                  |
| Welcome back                   |     |                  Welcome back                   |
| Find your account              |     |            Find your account                    |
| [ Account ID                 ] |     |       [ Account ID                 ] [ Search ] |
| [ Search                   ]   |     |                                                  |
|                                |     |                 or                               |
| New here?                      |     |       New here? [Create account]                |
| [ Create account             ] |     |                                                  |
+--------------------------------+     +--------------------------------------------------+
```

### Dashboard

```text
MOBILE — 375px                         DESKTOP — 1024px+
+--------------------------------+     +--------------------------------------------------+
| [Home] Account                 |     | [Home] Account                                  |
| AccountCard                    |     | AccountCard                                     |
|                                |     |                                                  |
| +----------------------------+ |     | +----------------------------------------------+ |
| | Vault balance              | |     | | Vault balance                                | |
| | $0.00                      | |     | | $0.00                                        | |
| +----------------------------+ |     | +----------------------------------------------+ |
| Deposit form                  |     | +----------------------+ +---------------------+ |
| [amount] [note] [Deposit]      |     | | Deposit form         | | Withdrawal form     | |
| Withdrawal form               |     | | ... [Deposit]        | | ... [Withdraw]      | |
| [amount] [note] [Withdraw]     |     | +----------------------+ +---------------------+ |
| Transaction history           |     | +----------------------------------------------+ |
| rows / empty message          |     | | Transaction history (scrollable)             | |
|                                |     | | rows / empty message                        | |
+--------------------------------+     +--------------------------------------------------+
```

### Create Account modal

```text
MOBILE — 375px                         DESKTOP — 1024px+
+--------------------------------+     +--------------------------------------------------+
| Home content dimmed           |     | Home content dimmed                            |
|   +------------------------+   |     |          +--------------------------------+      |
|   | Create account      [X]|   |     |          | Create account              [X]|      |
|   | Name                   |   |     |          | Name                           |      |
|   | [____________________]|   |     |          | [____________________________]|      |
|   | Email                  |   |     |          | Email                          |      |
|   | [____________________]|   |     |          | [____________________________]|      |
|   | Initial deposit        |   |     |          | Initial deposit                |      |
|   | [____________________]|   |     |          | [____________________________]|      |
|   | [Cancel] [Create]      |   |     |          | [Cancel]             [Create]  |      |
|   +------------------------+   |     |          +--------------------------------+      |
+--------------------------------+     +--------------------------------------------------+
```

At 375px the dialog is full-width minus 16px gutters, vertically scrollable if needed, with actions stacked or full-width if they cannot fit comfortably. At desktop it is centered with a maximum width of 480px; the backdrop remains visible around it. The same field and button order applies at both breakpoints.
