# Transaction structure and research cross-check

Review date: 2026-10-23. App parser version: 4. Scope: Claude's research notes and scripts across five device exports, excluding its design folder. Five devices are not five independent people: the research identifies one old/new phone pair. No new phone installation or import was performed in this review.

## What was checked

Read the main findings, concerns, vendor research, device-specific findings, and classification scripts. Reproduced nine reported parser weaknesses against the current Java source using non-personal synthetic messages (`scripts/CheckResearch.java`). Ran a local aggregate-only field study (`scripts/study_fields.py`) over the existing exports and their recorded v4 parser verdicts. These are candidate coverage measurements, not a labelled accuracy benchmark. The current connected phone is different from the previously studied CPH2617; its live audit was not rerun.

The original Android field audit missed the compact `ddMonyy` date format. Corrected its detector and verified that format locally in the research exports. The audit runner correction has not yet been rerun on CPH2617.

## Timing findings

On CPH2617, all 1,149 received messages contain 271 parser-accepted candidates. Of those, 260 contain a recognisable body date: 257 compact day/month-name/year, two separated month-name dates, and one numeric date. Only one has a clock time, and none has both a recognised date and clock. For 258 of the 260 dated candidates the body date equals the SMS arrival calendar date in Asia/Kolkata; two arrived the following day. No explicit timezone was detected.

On the other research datasets, recognised body dates / body clock times are: Pixel 10 Pro 8,906 / 6,561 of 9,196 candidates; Xiaomi 89 / 1 of 91; vivo 4,226 / 75 of 4,396 received candidates; Pixel 7 Pro 6,589 / 6,261 of 6,749. The old/new Pixel datasets overlap heavily and must not be pooled as independent evidence. Some dates precede SMS arrival by multiple days; vivo also contains dates after arrival, which can be due dates or false transaction candidates. Body-date detection alone does not establish an executed transaction date.

The vivo notes quote 4,403 accepts, whereas restricting the exported verdicts to received messages gives 4,396. Always state folder scope before comparing counts.

`SmsImport` currently reads only SMS body and `date`; Store saves that arrival timestamp as `time`. It does not extract an event timestamp. Claude's hourly spending code also uses that timestamp via the computer's local timezone, so its late-night spending observations concern SMS arrival, not established purchase time. Explicit Asia/Kolkata avoids dependence on the computer setting.

Recommended data contract:

- Preserve `receivedAt` and, where available, `smsSentAt` as transport metadata. The sender timestamp is not the transaction execution time.
- Store transaction body date separately with `precision=date`, or date plus clock with `precision=minute/second`. A date-only message must not invent midnight as its transaction time.
- Record timestamp source, parsing confidence, and whether the timezone was explicit or inferred from an issuer-specific format. Do not silently assume a timezone for arbitrary messages.
- Keep `dueAt`, `scheduledAt`, `refundExpectedAt`, and `receiptAt` separate from completed transaction time.
- Until event-time extraction is validated, label hourly analysis as SMS arrival timing. Use clear fallback provenance for budget boundaries, especially around midnight.
- Do not replace the arrival timestamp used by existing fingerprints when adding event time: that would undermine repeat-import identity.

## Details the structures can supply

Completed alerts can contain amount and currency, debit/credit perspective, payment rail, masked account/card identifier, counterparty text or UPI ID, transaction reference/RRN/UTR, date, time, reported balance, issuer, and support/dispute footer. Fields are optional and issuer/version dependent. CPH2617 has extracted names in 255 candidates, references in 257, account wording in 260, and balance wording in only two. Names and references have not been independently verified merely because the parser extracted them.

Mandate, bill and refund messages add different facts: mandate/order/loan identifiers, next execution/due date, maximum authorised amount, overdue amount, cancellation/failure status, and expected refund date. They belong to lifecycle events, not automatically to completed bank movements. A mandate ceiling is not a charge; a provider receipt is not necessarily a second debit; a broker balance is not bank income.

Account identifiers must be scoped by issuer, account/card kind, and source evidence. Masked last digits can collide. Bank names in origin clauses are not payees, and processors/aggregators do not reveal the underlying shop. Support phone numbers, URLs, available balances, promotional amounts and references must not be mistaken for transaction fields.

## Confirmed current parser problems

All nine synthetic probes reproduced the claimed failure class:

1. A loan advertisement saying funds can be credited is accepted as income.
2. `not debited` is accepted as a debit.
3. `Acct ... debited ... beneficiary credited` is rejected because own-account disambiguation misses `Acct`.
4. Kotak `Sent ... from Kotak Bank ... to SHOP` selects the bank as payee.
5. `Your beneficiary has received ... via NEFT` becomes incoming money instead of outgoing confirmation evidence.
6. `INR.` interest is rejected.
7. `will be refunded in ... days` becomes a completed refund.
8. Successfully processed debit-card e-mandate wording is rejected.
9. Future NACH presentment can become a completed debit when another word such as `sent` triggers debit detection.

These were confirmed as classes of bugs, not independent verification of every financial total in the research. No fixes to production parsing or phone ledger were made during this review.

## Useful learnings and corrections to Claude's approach

Issuer-specific, versioned templates with masked regression cases are strongly relevant. Prioritise false money and wrong direction before increasing category coverage. Sender context is currently discarded by import, so introduce source-aware extraction without persisting raw sender addresses. Header classification is supporting evidence, not authentication; service/transactional suffixes do not prove a completed bank movement, legacy headers exist, and promotional messages may still contain useful receipt evidence. Never promote a suspicious header into money purely from its suffix.

The prototype's receipt linker chooses the closest equal amount within an absolute six-hour window. It allows evidence before or after the debit, does not require a shared reference, and does not enforce one-to-one matching. The reported links are suggestions, not confirmed matches. Overdue reminders, setup/mandate messages, pending refunds and repeated small purchases are especially unsafe to merge this way. Prefer exact reference plus compatible amount, direction and issuer; otherwise require compatible merchant context, a narrow timestamp window, unique candidates, and user review.

The prototype's `VERIFIED` directory includes entries explicitly described as unconfirmed. Split evidence into verified business identity, category suggestion, and local user-confirmed rule. A legal payee and matching amount do not establish a particular subscription product or loan relationship. Do not turn a processor into the end merchant or infer identity from an ambiguous truncated name.

Person-like names, bare UPI handles and masked accounts do not establish P2P transfers or own-account movements. Small merchants often use personal names. Keep a category suggestion distinct from transfer exclusion. Likewise, employment, vehicle ownership or business status cannot be established from an OTP sender.

The reported 16/20/15-character cut-offs are useful observations, not universal issuer guarantees. `MerchantRules` itself does not truncate names: it keys on the full extracted name or VPA. Its collision risk arises when the upstream bank already truncated that name. Alias rules need source context and provenance; broad prefix matching can merge unrelated businesses.

The insight issue is real: reviewed-only rows gate insights, and recurrence additionally admits only Bills/Entertainment/Other. Adding Subscriptions without updating that filter would silently suppress it. Bank-stated scheduled events could be surfaced separately without pretending unreviewed spending is confirmed. Bulk review is useful, but should not blindly confirm an ambiguous alias group.

Room/SQLite and migration/import continuity are sensible next steps, but the research did not benchmark UI latency or prove ANRs. Fingerprints support exact copies only when timestamp and message text survive migration unchanged; differently formatted or delayed copies still need careful reference-based deduplication.

Existing research exports include raw SMS and personal ledger copies on this laptop, unlike our prior on-phone studies. Added `clauds work/` to `.gitignore` to prevent accidental source-control inclusion. No files were deleted or uploaded. The review tools emit aggregates or synthetic examples, not personal field values.

## Recommended implementation order

1. Regression-backed safety/direction fixes and sender/source-aware classification; refresh unreviewed ledger records without overriding reviewed corrections.
2. Separate completed movements, receipts, scheduled obligations, failed events and pending refunds with explicit provenance.
3. Extract transaction date/time and masked account context while preserving arrival metadata and import identity.
4. Conservative merchant aliases and high-confidence category suggestions, then review UX and lifecycle insights.
5. Reference-backed cross-message linking, storage migration and cross-device import validation.
