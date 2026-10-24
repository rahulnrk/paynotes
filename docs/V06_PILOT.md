# v0.6: connected development pilot

24 October 2026. The app stays local-first. Participating phones can explicitly opt into a development feedback service. A USB connection is unnecessary for enrollment, reports or APK updates.

## Start the admin

Requires Node 22.13 or later with `node:sqlite`. Node currently prints an experimental SQLite warning; the API uses the built-in database module.

From the project directory:

```powershell
./scripts/start-pilot.ps1
# Or keep a foreground process:
node admin/serve.cjs
```

Open http://127.0.0.1:4173. The first start generates `artifacts/pilot-service/secrets.json`. Enter `adminToken` in the admin login. The token is held in tab memory, not browser storage. `enrollCode` is for enrolling phones; never give testers the admin token. The admin can display the enrollment code after login.

Server data is in `artifacts/pilot-service/pilot.sqlite` (with SQLite WAL files). It survives browser closure and service restarts. Back up the database using SQLite's backup operation or while the service is stopped; copying only a live `.sqlite` file can miss WAL writes. Artifacts are excluded from source control. Snapshot/report data is deleted after 90 inactive days; deletion of an installation cascades to its reports/snapshot and revokes its token.

This is a single-process development/pilot service with bounded payloads, rate limits and authenticated admin endpoints. It is not an automatically provisioned cloud production deployment. Admin views currently cap the list at 2,000 installations and 1,000 reports. PostgreSQL/shared rate limiting, managed HTTPS, monitoring, key rotation and paginated fleet administration are needed before larger deployments.

## Phones away from the laptop

The Android client requires HTTPS and does not disable certificate checks. A phone cannot reach `127.0.0.1` on your laptop through that address.

For development you can run the already installed ngrok CLI yourself:

```powershell
ngrok http 4173
```

Use the generated **HTTPS** origin (no trailing path) on the phone. Keep the server and tunnel running. Tunnel startup by the agent was blocked by automatic approval review, so no public tunnel was started during this implementation. The local service remains available.

A temporary address can change. Entering a replacement tunnel address reuses an existing installation credential if the new address reaches the same server, avoiding a new enrollment. A different server requires enrollment again. Laptop sleep, shutdown or lost connectivity makes sync unavailable; the app retains local data and retries. This is a development arrangement, not always-on hosting.

Vercel's local filesystem cannot persist this SQLite service reliably across serverless instances. A Vercel deployment requires an external durable database adapter. A persistent host/volume or managed PostgreSQL is the next hosting step. References: https://vercel.com/kb/guide/is-sqlite-supported-in-vercel and https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/.

## Tester installation without USB

1. Give the tester the **release** APK (`app-release.apk`, see README "Build and install"), or have them download `/v1/app.apk` from the HTTPS service, which serves only the signed release. Android must allow installation from that browser/file app. Every update must be signed with the same release key. A phone that has an older debug-signed build must uninstall it once first; this removes its local data.
2. Enable SMS and/or notification capture through the app's disclosures and Android settings. Import received history on the phone; nothing requires exporting its inbox.
3. Optional: Settings → Join development pilot / sync. Enter the HTTPS origin and enrollment code, read the disclosure, agree and Join.
4. Preview the shared payload. It contains app/parser versions, cumulative counts, permission/connectivity booleans and at most 100 masked grammar summaries with registered six-character business sender codes (e.g. IDFCFB). Personal phone numbers and contacts are excluded. Arbitrary words/numbers are rejected by the server. It excludes amounts, merchant names, account data, references, raw SMS, notification bodies, OTPs and personal contact data.
5. With consent, changing a transaction name/category/direction also queues a masked `user_correction` case; these may reflect personal preference and are not verified parser failures. For a mistake, open a transaction → Report extraction issue, choose the issue and expected outcome, inspect the masked grammar and Submit. For a missed message use the Settings report action; the pasted text is masked locally and discarded. Background sync runs approximately every six hours when Android permits; Sync now is also available.
6. Check report progress and app update in the pilot dialog. Updates are downloaded and installed by the tester; the app does not silently install code or remote regexes.

Sharing is off by default. Stop sharing cancels future scheduled work; an in-flight request may finish. Delete shared pilot data removes the installation's server record. If offline, deletion must be retried; pausing still takes effect locally. Deleting all local app data does not erase already uploaded server data, so delete shared data first when possible. Reinstalling/resetting creates a new enrollment and is not a unique-person identity.

## The learning loop

New installation → opt-in grammar/coverage counts → user report → admin investigation → synthetic regression case → tested parser release → tester installs APK → subsequent snapshots identify the parser version.

Admin statuses are new, investigating, test added, released (with a fixed parser version), or dismissed. Exported cases contain masked grammar plus a typed issue, not raw financial examples. Developers must reconstruct synthetic positive and negative examples and verify the fix. Reports never become executable parsing rules automatically. A report marked released is a developer assertion, not independent proof that a test was run. Server snapshots replace previous uploads; replays do not increase fleet totals. The service does not establish financial accuracy from review coverage.

Diagnostic counts are deduplicated by observation identity on the phone. A full import after opting in supplies historical format coverage. The on-device pattern lab remains a separate exploratory view. It does not train a shared model. Raw research exports in `clauds work/` are never uploaded by the service.

## Financial changes

- Indexed SQLite movements, event records and message-identity aliases replace full JSON preference rewrites. Legacy ledger/event JSON migrates in a transaction; reviewed edits survive. Malformed migration refuses completion and retains original preferences. Settings and merchant rules remain in preferences.
- SMS identity uses normalized body + transient sender + network timestamp when available, keeping live capture/import aligned. Existing arrival fingerprints are also retained as aliases. No merchant/30-second suppression: two real rapid purchases survive. Compatible issuer/account/reference/amount/direction evidence links duplicate sources only when the match is unique.
- More false-credit filters cover IPO acknowledgements, loan offers, wallet bonuses, repayment notices and pass codes. Credit notifications require a supported bank app or a bank-header notification from a supported SMS app. Unrecognized sources may be missed and can be reported. Registered headers/package context is supporting evidence, not transaction authentication.
- Due-date extraction supports yearless month names, ordinal dates, apostrophe years and non-adjacent mandate dates. Inferred dates are labelled. Collect requests have their own type. Delivery failures are not payment failures.
- Recurring candidates support weekly, fortnightly, 28-day recharge, monthly, quarterly and annual patterns. Variable amounts show a range. Candidates include unreviewed observations with an explicit warning; user confirmation records a series status. Multiple products sharing one merchant cannot reliably be separated without additional identifiers.
- Financial events can be marked paid/received, cancelled or dismissed. Shared reference, amount and compatible account evidence can reconcile executed movements with events. Amount/time alone does not mark an obligation paid. Manual dismissal does not cancel an external mandate.
- Payment checks surface open collect requests, failed notices and possible equal charges with different references close together. These are review prompts, not fraud verdicts. There is no financial authentication or scam-reputation service.

## Remaining limits

Zero message coverage cannot prove zero spending. Some banks omit small payments, merchant names, references or clocks. Unsupported languages/templates remain. Legacy live-capture records without a common transport identity/reference cannot always be merged safely; old historical duplicates require review. Multiple similar reminders can still represent one obligation, so the app does not sum them as a debt total. Pay-later purchases and later settlements are not fully reconciled; broad BNPL completeness is not claimed. Partial refunds and automatic cancellation chains need further work. Existing narrow reviewed-monthly Insights remains alongside the broader Recurring payments screen.

The admin shows installation IDs, not unique people. A replacement phone may need enrollment again. Stable identity across phones requires an explicit transfer/recovery design, not guessed matching of personal histories. No raw transaction backend is introduced.

## Verification

```powershell
./gradlew.bat testDebugUnitTest assembleDebug lintDebug
node tests/admin.test.cjs
node tests/ai-server.test.cjs
node tests/pilot-server.test.cjs
```

Pure Java probes are in `tests/V06Test.java` in addition to existing parser/facts/insights tests. Robolectric host tests exercise actual Android storage classes without a connected phone. No physical handset is used for this release's tests. Real OEM background scheduling, notification delivery, TLS sync from a physical phone and sideload/update UX still need field validation.
