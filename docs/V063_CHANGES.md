# 0.6.3 — amounts you can trust and repair

App version code 9, parser version 8, database schema 2 (unchanged). Responds to the release blockers in [the 25 Oct 2026 MVP audit](MVP_AUDIT_2026-10-25.md). Installs over 0.6.2 in place when signed with the same project key.

## Audit findings addressed

| Finding | Change |
|---|---|
| **F01** wrong amount (P0) | The parser no longer takes the first currency match. Amounts after *balance, available, limit, outstanding, up to, worth* are states, not movements. One remaining candidate is used; several distinct candidates must be bound to the movement verb, otherwise the message is skipped rather than guessed. A malformed token such as `INR 10.999` rejects the message. |
| **F02** amount not repairable (P0) | Review now has an amount field validated as integer paise (max ₹100 crore). The first extracted value is kept as `originalPaise`; re-import and parser upgrades never replace a reviewed amount. Typing the original value back removes the override. Invalid input keeps the dialog open with an error. |
| **F03** event claims outlive evidence | Deleting a movement, changing its direction or excluding it re-opens automatic "Paid / received" event resolutions in the same transaction, then re-checks the match. Statuses the user set stay. |
| **F05** delayed SMS after reset | The SMS receiver captures the reset generation at receive time and drops a late write. A failed SMS write sets the visible Settings warning instead of crashing. |
| **F06** partial commits | Live capture (event, diagnostics, movement, identities, reconciliation) commits as one SQLite transaction. Import reconciliation moved inside the batch transaction. The widget refreshes only after commit. |
| **F10** release identity asserted, not verified | `assembleRelease` runs `apksigner verify` and writes `release.json` with the APK SHA-256, signer certificate SHA-256 and `debuggable:false` only on success. An unsigned build fails the task. The service offers `/v1/app.apk` only when the file's bytes match that record, hashes once per file version, and serves the exact verified bytes. |
| First use / recovery | A fresh install opens on source setup with capture status (SMS on/off/permission missing, notifications connected/not), not a ₹0 figure. Paste shows a preview of what will be saved before writing. Home and the widget show this month's money out, with the unreviewed count next to it. |

## Not addressed in this release

F04 (UI-thread queries, unrecycled list), F07 (Activity-owned import state), durable capture queue (rest of F05), F09 (pilot deletion response loss), F11 (AI budget) and the navigation reduction to Home/Activity/Settings remain open; see the audit roadmap, phases 1.3–2.

## Verification

- `gradlew.bat testDebugUnitTest`: 36 host tests, 0 failures, including `AuditFixesTest` (amount role, abstention, correction across re-import, paise validation, event release and user-confirmed status).
- Standalone `ParserTest`, `FinancialFactsTest`, `InsightsTest`, `V06Test` pass. The audit's probe now returns 50000 / 50000 / rejected.
- `node --test tests/*.test.cjs`: 5 pass, including unverified/mismatched release refusal.
- `assembleRelease lintDebug`: signed and verified, 0 lint errors.
- Not verified: physical-device lifecycle, OEM background capture and real bank-message recall. A skipped ambiguous message is a recall loss by design; report it from the app so a template can be added.
