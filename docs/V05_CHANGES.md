# v0.5 pilot: transaction trust and financial events

Implemented from the cross-device message study:

- Parser v5 rejects promotional headers, phone-number SMS senders, promotional loan wording, negated debits, future presentments, pending refunds, broker net-position wording and outgoing beneficiary confirmations as completed movements. It recognises ICICI `Acct`, `INR.`, destination payees in Kotak alerts, completed debit-card mandates, and ATM withdrawal evidence. More recharge/provider confirmations become receipts excluded from spending.
- SMS import and live capture use sender context transiently. The source address and raw message are not persisted. Notifications still rely on content evidence rather than a sender header.
- Structured scheduled payments, due notices, failures, mandate changes, pending refunds and outgoing confirmations live in a separate local event archive (latest 2,000), outside spending, budgets, recurrence and anomaly calculations. Exact re-imports are idempotent. These are message-stated evidence, not authenticated or reconciled obligations. An amount may be a stated ceiling or due amount; users must check the provider.
- Overview exposes the latest events and near-term dated bank-message obligations without requiring transaction review. Historical events remain accessible; automatic cancellation/payment reconciliation and OS reminder scheduling are not implemented.
- Transaction detail preserves arrival/posting timestamp, SMS sender timestamp where supplied, body date, body clock, source and optional masked account/issuer. Body dates and clocks are labelled separately, with no invented precision or timezone. Budget periods and fingerprints continue using existing arrival timestamps.
- Last bank-reported balance snapshots are extracted only with recognised issuer plus masked account context and an explicit balance amount. They are shown for SMS transactions, grouped by issuer and masked account/card identity, never advertised as live balances. Masked endings can collide; no calculated balance or credit-limit substitution is attempted.
- Review offers an explicit confirmation step for matching unreviewed extracted identities within the same source, issuer, channel and direction. It does not change direction or overwrite reviewed edits. Truncated-name ambiguity is disclosed. Individual review and exact saved rules remain available. Subscriptions is now a selectable category and participates in reviewed recurring-payment analysis.
- Receipt evidence with a matching reference cannot overwrite or demote a completed bank movement. Automatic amount/time-based receipt linking is intentionally absent.

## Updating existing history

After installing v0.5, re-import SMS in Settings to refresh unreviewed parser fields, exclude newly rejected false positives, and populate events and optional metadata. Reviewed corrections are retained. Existing notification-only history cannot be reparsed without the original notification; dismissed notifications are unavailable. No phone history was imported during development of this release.

The new event archive is cleared by the existing Delete local data action. Pilot metric and optional AI payload schemas remain unchanged; events, account identifiers, balances and source text are not included in shared metrics or cloud explanations.

## Verification

Parser regression probes cover the cross-device failure classes and source filters. Pure Java checks cover compact dates, invalid dates, clock precision, scheduled dates, negative reported balances, missing account context, lifecycle status and subscription recurrence. Emulator instrumentation covers event idempotency/spending exclusion, upcoming events before review, review-group boundaries, metadata persistence without raw bodies, receipt/movement separation, protected corrections, SMS broadcasts, batch/inbox re-import and UI rendering. Android assembly and lint are required before delivery.

### Pixel 10 Pro real-device check, 2026-10-23

Installed v0.5 and ran the non-destructive full-history runner with the owner's authorization. Scanned all 18,018 received messages (19,249 messages across folders). First import added 238 candidates, refreshed 9,135 matching rows, and marked 79 old unreviewed candidates Not a transaction. Repeat import added zero rows and kept the event count stable. The parser study identified 9,328 received candidates; retained ledger totals need not equal message candidates because shared references can deduplicate multiple messages.

The ledger has 9,391 rows including 79 excluded rows and 115 receipts. Among non-excluded rows, 9,037 have a body date, 6,633 a body clock, 8,737 masked account context, and 7,960 reported-balance snapshots. These are presence counts, not independently verified financial facts or distinct accounts. Events: 37 bill/EMI notices, 41 failed payments, four mandate changes, 40 outgoing confirmations, 66 pending refunds and 229 scheduled payments (417 total). No supported dated upcoming event fell in the next seven days.

The notification listener connected; four active notifications were ignored and re-scanning added zero records. A real financial notification was not available for validation. Overview, activity, insights, pattern lab and SMS controls rendered. Zero reviewed transactions means reviewed-only inferred insights remain empty, as designed. Categorisation remains limited: 7,896 non-excluded ledger rows are Other and 6,939 lack a merchant name. No accuracy claim follows from successful import.

The combined full import, repeat and study took several minutes on this history. The app process was actively using CPU during the run; no failure was reported. Large-history processing and the growing SharedPreferences ledger remain performance limitations. No raw SMS export or cloud request was made by this test; the inbox was untouched.

### Xiaomi 2606FRT34I real-device check, 2026-10-23

The owner enabled USB installation and granted SMS permissions through the phone UI. Installed v0.5 and imported all 201 received messages (213 across folders), producing 90 candidates. Repeat import added zero transactions and kept financial-event counts stable. Ledger: 80 debit-side records, including one receipt excluded from spending, and ten credits; two unknown names, one missing reference, 70 Other categories and zero reviewed entries. Body dates are present on 89 candidates, clock time on one, masked account context on 79 and supported balance snapshots on none. Zero balance snapshots means absent supported evidence, not a zero account balance.

One failed-payment event and one pending-refund event are stored outside spending. No supported upcoming event fell in the next seven days. App screens rendered and no cloud request was made. Notification access was not connected on this run; its settings were opened for the owner to grant access before a follow-up check. Extracted names/categories have not been manually confirmed.

Follow-up: repeated full import again added zero rows and refreshed none. The listener remained disconnected. Initially the secure enabled-listener setting included CaptureService, but Activity Manager showed no bound service. Subsequent notification-manager and secure-setting checks no longer included Prardhana in the allowed list. Permission persistence/user confirmation remains unresolved; no notification-service code was changed without a confirmed application fault. Notification access settings were reopened for the owner to inspect the current toggle and any warning.

Final follow-up after the owner's phone confirmation: notification permission persisted and the listener connected. Scanned eleven active notifications, ignored all eleven, and repeated scanning added zero records. SMS re-import again added zero and refreshed none; event counts remained stable. Notification access and active scanning are verified, but no real financial notification was present to validate payment extraction. The test helper was removed after verification; v0.5 and local data remain installed.

### Pixel 7 Pro real-device check, 2026-10-23

Installed v0.5 and scanned all 14,098 received messages (14,910 across folders). First import added 155 candidates, refreshed 6,747 matching rows and excluded 41 old unreviewed candidates. Repeat import added zero rows; 6,863 message candidates were recognised as duplicates. Ledger: 6,902 total rows including 41 excluded and 56 receipts. Non-excluded rows contain 6,720 body dates, 6,332 body clocks, 6,543 masked account contexts and 5,896 reported-balance snapshots. These are field-presence counts, not verified distinct accounts or live balances.

Separate events: 15 bill/EMI notices, twelve failures, one mandate change, 27 outgoing confirmations, 51 pending refunds and 180 scheduled payments (286 total). Repeat-import event count was stable; no supported upcoming event fell within seven days. The listener connected and ignored eleven active notifications; repeat scanning added zero rows. Real financial-notification extraction remains unverified. App screens rendered and no cloud request was made.

Limitations: full-history processing again took several minutes. There are 6,554 non-excluded entries with unknown merchants and 5,994 Other categories. Zero reviewed rows means inferred insights remain empty. This phone's history overlaps the Pixel 10 Pro and must not be counted as an independent participant corpus. Raw messages stayed on the phone during this test. Removed the test helper and left the app installed after verification.
