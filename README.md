# Prardhana — connected Android pilot

Local-first expense tracking, recurring-payment candidates, financial events and an opt-in development feedback service. Version **0.6.2 / parser 7** is a development build, not a verified financial ledger or fraud service.

Transactions now live in indexed SQLite. Legacy preferences migrate with reviewed corrections preserved. Pending/failed payments stay outside spending. Testers can enroll over HTTPS and report masked message formats without connecting phones to a laptop. No raw SMS, merchant names, amounts, accounts or payment references are uploaded to the pilot admin.

See [0.6.2 changes and upgrade limits](docs/V062_CHANGES.md) for conservative duplicate handling, durable deletions, background writes and monthly message history.

## Build and install

Use JDK 17 and Android SDK 35. Set ANDROID_HOME, then:

- **Testers:** `./gradlew.bat assembleRelease` → `app/build/outputs/apk/release/app-release.apk`. It is not debuggable, so `adb run-as` cannot copy a tester's ledger, and it is signed with the project release key. The pilot service offers only this file at `/v1/app.apk` (with its SHA-256 at `/v1/release`). It never serves a debug build. Share it directly or let testers download it; USB is unnecessary.
- **Development only:** `./gradlew.bat assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk` (debuggable, laptop debug key). Never give this to testers.

**Release key.** `keystore.properties` in the project root (git-ignored) points to the keystore and holds its passwords:
```
storeFile=C:/Users/<you>/.prardhana/signing/prardhana-release.jks
storePassword=…
keyAlias=prardhana
keyPassword=…
```
Without this file the release APK is built unsigned and the pilot service has nothing to offer. **Back up the keystore and `keystore.properties` together, offline.** Every future update must be signed with the same key; if it is lost, testers must uninstall and lose local data. Keep both out of chat, email and cloud folders that others can read.

**Switching a phone from a debug build.** Android refuses to update an app signed with a different key. Phones that have the old debug-signed build must uninstall it once before installing the release: local transactions, reviews and settings are removed. Re-importing SMS restores transactions but not reviews. Delete shared pilot data in the app first if the phone had joined the pilot.

In Settings, enable SMS and/or notification capture after the disclosure and Android permission prompt. Import received history on the phone. Review transaction details and inspect Recurring payments, financial events and last bank-reported balances. Missing messages can hide spending.

## Start the admin

Requires Node 22.13+ with node:sqlite:

```powershell
./scripts/start-pilot.ps1
# Or: node admin/serve.cjs
```

Open http://127.0.0.1:4173. Credentials are generated in `artifacts/pilot-service/secrets.json`; use adminToken to log in. Persistent data lives in `artifacts/pilot-service/pilot.sqlite`. Credentials and databases are excluded from source control.

For phones away from the laptop, run an HTTPS tunnel yourself, e.g. `ngrok http 4173`. Testers enter its HTTPS origin and the separate enrollment code in Settings → Join development pilot / sync. No public tunnel was started by the agent: automatic approval review blocked its launch. The service remains local until you establish a tunnel or deploy it.

Opted-in phones send aggregate snapshots and masked format counts approximately every six hours when Android allows, plus Sync now. Each installation has a revocable token. Reports move through new → investigating → regression test added → released. A tested APK delivers parser changes; uploaded reports never execute remote code or automatically train the parser. Legacy metrics-file import is still available.

See **[v0.6 setup and limits](docs/V06_PILOT.md)** for tester steps, consent/deletion, migration, temporary addresses and the learning loop. Earlier context: [v0.5](docs/V05_CHANGES.md), [message patterns](docs/MESSAGE_PATTERNS.md), [research review](docs/RESEARCH_REVIEW.md).

## Verification

```powershell
./gradlew.bat testDebugUnitTest assembleDebug lintDebug
node tests/admin.test.cjs
node tests/ai-server.test.cjs
node tests/pilot-server.test.cjs
```

Host Android tests use Robolectric and require first-run Maven downloads. They cover migration/rollback, capture/import identity, genuine rapid purchases, account separation, event status, consent, replay counts, large batches and screen entry points. Standalone Java probes include tests/V06Test.java and the existing parser/facts/insights tests. No physical phone is needed. OEM background behavior and real-phone HTTPS sync still need field validation.

## Limits

- This is a single-instance SQLite pilot backend. Production needs durable hosting, fleet pagination, managed secrets/backups and shared rate limiting. Vercel serverless needs a durable external database adapter.
- Recurring payments includes weekly through annual candidates and variable amounts. Confirmation is a local annotation; it does not cancel a provider mandate. One merchant can bill multiple products. The older reviewed-monthly Insights remains available too.
- Reconciliation requires a shared reference, amount and compatible account evidence. Ambiguous reminders, partial refunds, pay-later purchases/settlements and legacy duplicates still need review. A forecast or mandate ceiling is not a confirmed bill.
- Payment checks are review prompts, not proof of fraud. Unsupported formats/sources can be missed. Sender headers provide context, not authentication.
- Financial metadata stays on the phone; optional AI receives previewed numerical findings. The private research exports in clauds work are never uploaded.
- Server IDs represent installations, not unique people. Reset/reinstall may require new enrollment. Sharing is opt-in. Local deletion does not remove earlier server uploads; use Delete shared pilot data first.

Android references: https://developer.android.com/reference/android/service/notification/NotificationListenerService and https://support.google.com/googleplay/android-developer/answer/9888170.
