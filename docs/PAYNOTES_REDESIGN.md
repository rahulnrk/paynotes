# Paynotes 0.7 — current-screen redesign

## Core value loop

Open Paynotes → understand recorded spending for a selected period → compare the same point in the previous period → inspect the payments responsible → correct an entry or choose a target. Capturing messages supports this loop; it must not dominate the home screen.

## What was wrong and what changed

| Problem | Product consequence | Implemented response |
|---|---|---|
| Overview mixed balances, forecasts, events, capture controls and diagnostic content | Users had to decide what mattered before understanding their spending | One primary spending total, Day/Week/Month, prior-period comparison, chart, category breakdown and payment drill-down |
| Setup controls competed with routine use | Returning users repeatedly encountered permissions and import machinery | Two source rows and one default import action in Settings; one contextual connect action when the ledger is empty |
| Activity mixed review queues with ordinary browsing | Finding a payment required understanding internal states | Newest-first list with combined account, transaction-type, date, category and literal name/reference search |
| Generated insights looked authoritative without enough evidence | Missing capture could be interpreted as improved finances | Explicit recorded-data language, local summaries, optional conversational AI and no automatic target changes |
| Settings was a development console | Routine profile, connection and help tasks were buried | Grouped profile, money, connections and privacy/help; experimental diagnostics behind Pilot tools |
| Screen calculations and view construction were intertwined | More screens increased main-thread work and inconsistent totals | Calendar calculation and SQLite read models separated from native presentation; chart/list reads use workers |

The audit's earlier three-tab proposal is superseded by the user's four-tab brief. The application ID and local database remain compatible with existing installs. Paynotes is the visible brand; historical repository/package identifiers remain unchanged.

## Screen behavior

- **Overview:** Week by default; Day/Week/Month; previous/next period and date picker. Current periods compare equal local elapsed time. Shorter previous months use all available days once their end is reached. Month charts preserve previous-month days even when month lengths differ. Bars and totals use actual records; sample fixtures never enter production summaries. Selecting a bar or category opens matching Activity filters.
- **Activity:** exact issuer/account identity, not substring matching. Search escapes SQL wildcard characters. Filters combine and reset together. Rows open evidence and the existing amount/name/category correction flow. Results load in bounded pages into a native list.
- **Insights:** a local conversation works offline for monthly comparison, category summaries and target guidance. It identifies itself as local. A configured HTTPS service enables open-ended AI conversation. The user previews the bounded conversation and numerical totals before sending. Accounts, raw SMS, references and payees are not automatically attached. Typed conversation may itself contain personal data. The assistant cannot save targets; a separate explicit form does that.
- **Settings:** local display name, target, reported account balances, message sources, optional AI connection, privacy/reset and help. Import defaults to 90 days; all received history is an optional action and remains subject to the existing 5,000-message cap. Denied inbox permission leaves manual input available. Existing inbox access can be reused without demanding new-message access again.

## Technical constraints and remaining risks

- Message arrival is the time basis; this is not a bank statement. Missing messages, parser uncertainty and unidentified own transfers affect comparisons. No zero-filled chart can establish that no spending happened.
- SQLite is the source of truth; no new sync layer or account backend was introduced. Existing amount corrections, durable deletion and duplicate handling remain in place.
- AI uses `/chat` on the configured server's origin, alongside the existing `/insights`. Deploy the updated `server/insights.cjs` behind HTTPS before using the new AI mode. Secrets remain server-side except the existing encrypted client access token.
- Chat accepts at most 12 bounded turns and 16,000 characters, with a 32 KB request body and 1,200 output-token ceiling. Chat and explanations share the existing pilot limit of 30 requests/hour and 2 concurrent requests. This shared pilot quota is not a production tenant quota or a spend cap. Production needs per-installation authentication, metering and a hard provider budget.
- Slow AI requests use a separate worker from ledger reads. Network errors produce an explicit failure reply. Reset generations prevent late writes from reviving deleted chat; clearing a conversation invalidates pending replies. Import status persists across Activity recreation. Process-killed imports can be retried safely through existing duplicate handling.
- Some older secondary dialogs still live in `MainActivity`. Native string resources/localisation and larger-screen layouts remain debt. Adding a second UI framework would worsen this; migrate one bounded feature at a time.
- Emulator checks do not establish OEM background capture reliability, distribution eligibility for SMS access, or real-provider response quality. These require device/provider validation before rollout.

## Phase 1: Core Engine & Essential UI

Implemented in this change: four destinations, shared design primitives, real period comparison, filtered payment drill-down, local conversation, optional AI transport, explicit target editing, simpler source setup and Paynotes branding.

Release gate: build/test/lint pass; emulator screenshots reviewed; upgrades retain the same application ID and signer. Keep the current parser correction and deduplication regressions passing.

## Phase 2: Refinement & Error Handling

Implemented here: bounded background reads, stale-result guards, escaped combined search, calendar-boundary tests, import status persistence, chat size/rate bounds and honest offline/error states.

Next work in order:

1. Exercise revoked permissions, interrupted 5,000-message imports and duplicate notification/SMS evidence on the target OEM devices. Record skipped/recovered counts; never silently imply complete history.
2. Move import orchestration to lifecycle-independent durable work with resumable progress; current retries are safe but not a resumable job.
3. Add aggregate caching only after measuring query latency on large real ledgers. Invalidate on import, edit, delete and account reclassification.
4. Collect user corrections to validate category reliability and own-transfer classification. Surface uncertainty where it changes the spending interpretation.
5. Validate AI conversations with actual users before expanding scope: one goal, explicit assumptions, editable proposed target, no autonomous writes. Add tenant budgets before wider AI access.
6. Extract the remaining legacy dialogs and strings into feature modules/resources, with accessibility and localisation checks.

## Phase 3: Post-MVP Backlog

Defer onboarding/account creation and their permission requests as instructed. Also defer financial-health scores, streaks, decorative rings, deep analytics, automatic savings claims, predictive bills on the home screen, account aggregation, cloud sync, investment views, autonomous coaching actions and promoted diagnostic screens.

Borrow fitness products' metric → time window → history → detail interaction. A financial equivalent of a recovery score would require evidence this MVP does not have.

## Reproduce checks

```powershell
./gradlew.bat testDebugUnitTest assembleDebug lintDebug
node --test tests/admin.test.cjs tests/ai-server.test.cjs tests/pilot-server.test.cjs tests/chat-server.test.cjs
./gradlew.bat assembleDebugAndroidTest '-PpilotRunner=in.prardhana.app.PaynotesDesignChecks'
# Install the app and test APK ONLY on a disposable emulator, then run:
adb -s emulator-5580 shell am instrument -w in.prardhana.app.test/in.prardhana.app.PaynotesDesignChecks
```

The emulator-only runner refuses physical devices, seeds fictional payments and exercises four tabs, comparisons, filters, local chat, source controls and empty states. It writes screenshots under the app's external `design-checks` directory. Fixtures are in androidTest and never ship in the production APK.

Research links, typography, palette, spacing and interaction principles are in [DESIGN.md](../DESIGN.md).

## Build verification — 26 October 2026

- 40 Android host tests passed (ledger, audit fixes, correctness, calendar comparisons and combined filters).
- 8 Node subtests passed, including chat validation, provider contract, authentication and shared quotas.
- `assembleRelease` succeeded and its release metadata verified the APK signature; version 0.7.0 / code 10, non-debuggable.
- `lintDebug`: 0 errors, 36 warnings. Remaining warnings include localisation, legacy preferences/backups and small draw allocations. The existing AGP 8.5.1 / compileSdk 35 compatibility warning remains.

- Final emulator walkthrough passed after the Activity row fix, including an assertion that the payment amount is visible. Twelve actual screenshots are in `artifacts/paynotes-final/`; the gallery is `artifacts/paynotes-preview.html`. Fixtures are fictional.
- The emulator needed recovery from a System UI ANR after host sleep. A later cold-start run hit the 10-second UI wait threshold; a subsequent complete run passed. This is not a measured cold-start performance guarantee.
- Live OpenAI calls, OEM background capture, and large-font/landscape layouts were not validated in this pass.
