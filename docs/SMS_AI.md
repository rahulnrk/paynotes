# SMS capture and AI insights — v0.2

## On the phone

Settings → **Enable new SMS capture** shows a disclosure and requests `RECEIVE_SMS`. Declining leaves notification capture and manual paste available. New multipart messages are joined and processed off the receiver thread. Only messages accepted by the transaction parser produce stored transactions; sender numbers and original bodies are not saved.

Settings → **Import last 90 days of SMS** separately requests `READ_SMS` and scans the newest 5,000 inbox messages within the period on a background thread. Results report scanned, added, and duplicate counts. Turning SMS capture off stops further import work. Revoking permissions, leaving the inbox unavailable, or deleting local data stops processing safely. Live capture still needs `RECEIVE_SMS` even if inbox permission was granted.

Original transaction fields and a SHA-256 message/time fingerprint are retained locally. Exact re-imports are ignored. Shared references plus direction and amount deduplicate SMS and notification copies. Distinct references are not collapsed just because two purchases are close together. Cross-source messages without a shared reference can still appear twice; users can delete a duplicate in Activity.

Re-import can refresh extracted fields on unreviewed matching entries after parser improvements. User-reviewed entries are preserved. Previously accepted entries now rejected by the parser are retained as **Not a transaction** and excluded from spending and insight calculations; users can correct the category to include one again. No SMS inbox records are modified or deleted. Activity initially displays the newest 100 entries and offers further batches.

Settings → **Import active notifications** scans notifications currently visible with granted notification access. It does not access Android's dismissed-notification history. Repeating this import uses the same duplicate protections as automatic capture.

Android can restrict SMS permissions based on installer, device policy, or permission history. Do not bypass Play Protect or Android restrictions. Continue testing notification/manual capture where permissions are unavailable. The app does not become the default SMS handler and never requests permission to send SMS.

Google Play lists SMS-based money management among its exceptions. Eligibility is not approval: complete the Play Console permissions declaration, privacy policy, disclosure, and review before distributing through Play. Reference: https://support.google.com/googleplay/android-developer/answer/10208820

## Local insights

Open **Insights** after reviewing transaction merchants and categories in Activity.

- Recurring payment: three reviewed debit payments to the same merchant, 26–35 days apart. The first two amounts must be within 5%; the latest must either be within 5% or at least 10% higher. Only Bills, Entertainment, and Other categories are considered. Old patterns stop appearing after the estimated due date is more than seven days past.
- Price change: at least a 10% increase in an otherwise monthly series. Presented as a possible increase, with plan changes and one-off fees acknowledged.
- Upcoming payment: the next calendar-month date falls within seven days. An in-app estimate, not a confirmed due date or a scheduled OS notification.
- Unusually large purchase: a reviewed debit within the last seven days, at least ₹500 and three times the median of at least eight earlier reviewed purchases in the category over 90 days. This does not establish fraud.
- Budget warning: recorded spending reaches 80% of the chosen daily, weekly, or monthly limit. Totals can include unreviewed captured transactions. Transfers, future-dated entries, and sample entries are excluded from insight evidence.

Credits, transfers, unreviewed rows, future dates, and sample entries do not establish recurring or anomaly patterns. Missing capture means incomplete evidence. These detectors are statistical rules, not a language model. The UI explicitly distinguishes local evidence from model-generated explanations.

## Optional AI explanation server

The server uses the OpenAI Responses API with structured output and `store: false`. OpenAI reference: https://developers.openai.com/api/docs/guides/structured-outputs?api-mode=responses . Provider data policies still apply; `store: false` is not a guarantee of zero retention.

Prerequisites: Node.js 22+, an OpenAI API key, a model supporting structured outputs, and a trusted HTTPS reverse proxy to this pilot service. No provider credentials are bundled, and this checkout has not made a live model call.

Set `OPENAI_API_KEY`, `OPENAI_MODEL`, and `PILOT_ACCESS_TOKEN` in the server process environment. `server/.env.example` documents the variable names; the server does not automatically load `.env` files. Generate a random pilot token using:

```
node -e "console.log(require('node:crypto').randomBytes(32).toString('hex'))"
node server/insights.cjs
```

The service listens only on `127.0.0.1:4180`. Put it behind HTTPS on a server you control; the Android app rejects cleartext HTTP and redirects. Do not expose the development server directly to the Internet. `GET /health` reports whether provider configuration is present. No requests or financial payloads are logged or stored by this service. Your reverse proxy must likewise avoid logging request bodies or authorization tokens.

In Android **Insights → Configure AI connection**, enter `https://your-pilot-server/insights` and the pilot token. Never enter the OpenAI key in Android. The pilot token is encrypted with an Android Keystore AES-GCM key. The URL is saved locally. **Disconnect** removes the saved connection and in-memory explanations.

Choose **Generate AI explanations**. The confirmation shows the destination, the forwarding provider, and the exact JSON. It contains only numbered finding IDs, type labels, and numerical facts such as amounts in paise, payment counts, and days until an estimate. No free-text merchant names, phone numbers, SMS text, account references, transaction IDs, or precise transaction dates are sent. These remaining financial facts are still sensitive, so each request needs an explicit user action.

The model explains existing evidence; it cannot create new transaction records or act on money. Responses are schema-validated and matched to the submitted IDs. Errors, refusals, missing config, rate limits, and invalid output leave local insights usable. Explanations stay in memory and are hidden when the facts change. AI language can still be inaccurate; the original evidence remains visible above it.

This is a small pilot service: one shared access token, up to 30 authenticated requests per process per hour, and two concurrent calls. Before broader deployment, replace the shared token with real per-user authentication, persistent quotas, operational monitoring, and appropriate consent/retention controls.

## Verification

```
javac -d tests/build app/src/main/java/in/prardhana/app/TransactionParser.java app/src/main/java/in/prardhana/app/DuplicatePolicy.java app/src/main/java/in/prardhana/app/InsightEngine.java tests/ParserTest.java tests/InsightsTest.java
java -cp tests/build ParserTest
java -cp tests/build InsightsTest
node --test tests/ai-server.test.cjs
node tests/admin.test.cjs
```

Build and Android checks: `gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`.

`PilotInstrumentation` is an emulator-only harness. Run it only on a disposable emulator with synthetic SMS; it temporarily replaces and then restores application preferences. Install both debug APKs, grant SMS permissions on that test emulator, then run:

```
adb shell am instrument -w -e reference PILOTSMS123456 in.prardhana.app.test/in.prardhana.app.PilotInstrumentation
```

When it prints `READY_FOR_SMS`, use a second terminal:

```
adb emu sms send 5550123 "Paid INR 420 to Pilot Merchant ref PILOTSMS123456"
```

Populate a synthetic inbox fixture as well to exercise historical imports rather than an empty inbox. The harness checks actual SMS broadcast capture, import/re-import, duplicate handling, cloud payload redaction, encrypted token round-trips, and insight/settings rendering. Provider tests use simulated responses; live AI verification remains pending server credentials and HTTPS configuration.

### Physical-phone checks

The default `PilotInstrumentation` refuses to run fixture/reset checks on a physical phone. For an explicitly authorized real-data test, build the separate runner with `gradlew.bat :app:assembleDebugAndroidTest "-PpilotRunner=in.prardhana.app.RealDeviceChecks"`. Install the app and test APK on the selected phone and grant SMS and notification access, then run:

```
adb -s PHONE_SERIAL shell am instrument -w -e authorization local-phone-test in.prardhana.app.test/in.prardhana.app.RealDeviceChecks
```

This runner requires the explicit authorization argument, imports real recent SMS, checks repeat imports, imports active notifications, computes insights locally, and renders overview/activity/insight/settings controls. It does not clear app data, delete SMS, change reviewed entries, or call the AI provider. Diagnostic output contains aggregate counts and grammar shapes with names and numbers masked. It can update unreviewed extracted fields as described above. Use only with the device owner's authorization. Remove the test APK after finishing; the main app and its data remain installed.

Physical testing exposed and fixed bank SMS containing both an own-account debit and a beneficiary credit, concatenated UPI reference labels, beneficiary extraction after numeric dates, recharge confirmations misread as income, and payment reminders containing the word “paid.” Synthetic regression fixtures cover these formats without copying personal messages into the repository. Automated import checks do not establish ground-truth transaction accuracy; user review is still required.
