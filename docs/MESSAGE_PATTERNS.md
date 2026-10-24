# Message intelligence and repeatable local study

The application reads messages on the device and imports structured candidates, not an off-device copy of the SMS archive. Raw SMS bodies and sender addresses are not exported or retained in the study. The source inbox remains untouched.

## Study, import, review, repeat

1. Settings → Study message and notification patterns → Run full message study.
2. The study scans every available SMS folder. Received and sent counts are separate; drafts or other provider folders can contribute to the total. Only received messages qualify for ledger import. Message-family counts are parser classifications, not validated ground truth.
3. Settings → Import complete received SMS history imports completed candidates without the original 90-day or 5,000-message limit. Processing uses 500-message batches, hash/reference indexes, and saves each batch. Interruptions can be resumed by rerunning the import. A partial import is retained rather than discarded.
4. Activity offers local search across names, original extracted names, UPI IDs, categories, references, channels, and transaction types. Review queues separate missing names, uncategorized entries, incoming/outgoing money, transfers, refunds, and excluded candidates. Lists initially render 100 matching entries.
5. Review an entry to correct its name, category, and money direction. Optionally remember a name/category rule for the exact UPI ID or extracted name. Those rules apply to matching unreviewed records and future captures. They do not mark other records verified. Unknown entries without a stable name or UPI ID cannot share a global alias.
6. Repeat the study after parser or rule changes. The latest five masked snapshots remain on the phone with parser version, time, message counts, candidate coverage, missing-name/reference counts, and common grammar patterns. These are coverage measurements; lower unknown counts alone do not prove higher accuracy.

The optional Learn masked notification patterns switch stores at most 100 distinct masked grammar patterns while notification capture is enabled. Counts are observations, including repeated imports, not unique transactions. Raw notification text is discarded. Disabling the switch stops further learning. Delete local pilot data clears studies, patterns, and saved rules as well as the transaction ledger.

Only active notifications and new events are available through the listener. Dismissed Android notification history cannot be recovered by this implementation.

## Format hierarchy

The extraction pipeline distinguishes status and purpose before accepting a completed money movement:

- OTP and verification messages: ignored for the ledger.
- Bill reminders, statements, outstanding balances, future mandates, collect requests, failed payments, and pending refunds: not completed transactions.
- Own-account debit versus beneficiary credit in the same alert: own-account movement determines the direction.
- Prepaid recharge balance credit: not bank income.
- Loan/card payment receipt acknowledgements: not treated as money received by the user; avoid counting the lender's receipt as the user's income.
- Incoming credits: Money received by default. Explicit salary, interest, cashback, and completed refunds receive separate types/categories.
- Debit candidates: Payment, Cash withdrawal, or explicit Fee, with channel metadata for UPI, NEFT, IMPS, RTGS, Card, ATM, or unspecified bank movement.
- Ambiguous, unsupported, or incomplete financial messages: retained in masked pattern counts for future parser work, not guessed into the ledger.

Every imported candidate retains extraction provenance, parser version, channel, transaction type, optional VPA, and original extracted merchant identity. User-reviewed names/categories/direction survive re-import. Improved parsing can refresh unreviewed fields. Previously accepted entries rejected by the new parser remain visible as Not a transaction and are excluded from totals.

Category keyword matching uses word boundaries. A person's name containing a short brand string is not sufficient to classify it as transport or shopping. Bank transfer rail and spending category are separate concepts: a UPI transaction is not inherently shopping, and an incoming NEFT is not inherently salary.

## Resolving UPI names

Resolution order is: user-confirmed identity → exact saved local alias → explicit identity in the payment message → unresolved UPI identifier or missing name. A UPI handle suffix does not establish the merchant name, whether the recipient is a business, or its spending category.

A richer payment-app notification can enrich a bank alert when reference, amount, and money direction match. This is labelled Matched payment reference. A later bank-history import preserves that matched name rather than replacing it with Unknown merchant. User-reviewed identities remain protected. Similar amounts or nearby timestamps across different apps, without a shared reference, do not establish the same payment or recipient.

The app does not send personal VPAs, phone numbers, account identifiers, references, or message text to a search engine. General public web research can help understand known merchant brands and payment formats, but does not prove who owns a private UPI ID. Category and identity remain separate, and user confirmation is retained as the strongest local evidence.

Razorpay documents a VPA validation response containing an account-linked `customer_name`. Its current documentation also restricts the illustrated collect-flow endpoint to exempted businesses following collect-flow deprecation. This is not a universally available anonymous name directory. No provider lookup is implemented or called by this pilot. A future integration needs an eligible provider account, server credentials, documented allowed use, per-lookup disclosure, and verified response provenance. Account-linked names may still differ from the brand a user recognizes.

Sources:

- https://razorpay.com/docs/payments/payment-methods/upi/vpa-validation/
- https://www.npci.org.in/what-we-do/upi/faqs

## Developer checks

Synthetic parser fixtures cover own-account/beneficiary direction, UPI reference variants, generic credits versus income, explicit salary/interest/cashback, lender acknowledgements, prepaid recharges, future debits, and bill reminders. Emulator tests exercise exact VPA aliases, masked grammar, batch import/re-import, distinct references, and protection of reviewed fields. No real personal message is copied into test fixtures.

### Parser v4: second-device formats

An independent phone corpus exposed bank UPI alerts with an amount immediately after `debited by` but no currency prefix. These are accepted only with an account-scoped completed debit/credit, an explicit UPI label, and a reference label. Arbitrary bare numbers and setup messages remain excluded. A `RefNo` label ends the payee-name field. Strong ATM-cash evidence takes precedence over a generic bank UPI header.

Bill-payment or lender receipts are now Payment acknowledgement / Payment receipt evidence. They do not count toward spending, recurring-payment inference, or anomaly baselines until a user assigns a spending category. This prevents double counting a bank debit plus a provider receipt. Credit-card words in an advertising footer do not establish card funding. Explicit spent-on-card and used-on-card dialects are covered by synthetic fixtures; a corpus containing only card marketing and OTPs does not validate actual card purchases.

Non-bank recharge credits are not bank income. Foreign-currency amounts are deliberately unsupported by the INR ledger, so an INR available balance or credit limit cannot be misread as the foreign purchase amount. The full format audit separately reports card/UPI wording, unsupported formats, and possible classification risks; those counts are diagnostic signals, not ground-truth accuracy percentages.

Full-device audit runner:

```
gradlew.bat :app:assembleDebugAndroidTest "-PpilotRunner=in.prardhana.app.PatternStudy"
adb -s PHONE_SERIAL shell am instrument -w -e authorization full-local-study in.prardhana.app.test/in.prardhana.app.PatternStudy
```

Full import + repeat check + saved study, only after the device owner authorizes it:

```
gradlew.bat :app:assembleDebugAndroidTest "-PpilotRunner=in.prardhana.app.RealDeviceChecks"
adb -s PHONE_SERIAL shell am instrument -w -e authorization local-phone-test -e history all in.prardhana.app.test/in.prardhana.app.RealDeviceChecks
```

Both runners operate on the selected phone. The audit runner is read-only. The full-import runner appends candidates, refreshes unreviewed extraction fields, and can enable masked notification learning for the requested study. It does not delete SMS or reviewed entries, send data to the AI server, or create payment transactions. The emulator fixture runner refuses physical devices.

### Second-device v4 read-only verification

The post-update study scanned 1,172 messages (1,149 received, 23 sent) and identified 271 candidates: 255 UPI payments, two ATM withdrawals, four bank/unspecified records, and ten payment acknowledgements. Sixteen candidates lacked a merchant name and fourteen lacked a reference. The prior v3 study found only thirteen candidates in a corpus with one fewer message; these are extraction coverage counts, not measured accuracy.

The ten acknowledgements include eight bill-payment confirmations with card advertising in the footer. They are receipt evidence, not validated card purchases, and are excluded from spending by default. No actual card-purchase coverage claim follows from this corpus.

The authorized full-history check subsequently imported 271 candidates from all 1,149 received messages without truncation. Repeating the import added zero rows and recognised all 271 as duplicates. The ledger contains 268 debit-side records (including ten receipts) and three credits; 241 remain in Other, sixteen have unknown merchants, and fourteen lack a reference. No entries were marked reviewed, so the reviewed-only local insight engine returned zero insights. Extraction coverage does not establish semantic category accuracy.

The notification listener connected and scanned two active notifications, ignoring both; a repeated scan added zero records. This verifies access and non-financial rejection, but does not validate capture of a real financial notification on this device. Overview, activity, insights, pattern study, and SMS controls rendered without a test failure. The original inbox was untouched and no cloud requests were made.
