# 0.6.2 — conservative identity and useful message history

App version code 8, parser version 7, local database schema 2. This release changes storage, capture and presentation; it does not introduce new SMS extraction templates.

## Changes

- Removed automatic branch-alert pairing based on amount and time, including destructive repair of existing rows. Reference-free alerts remain separate, even when they may be copies. This prevents separate ATM and UPI payments from being silently collapsed.
- SMS-app/SMS body matching requires the same bank and masked account, a unique candidate, opposite source types, and a one-time pairing marker. Repeated same-source notifications remain separate. Equal SMS arrival timestamps no longer override distinct network identities.
- New deletion markers retain hashes of known evidence. Deleting an entry prevents re-import of those same known SMS identities, including changed arrival timestamps when the network timestamp is available. Full local reset clears the markers. Copies that were never observed cannot always be identified as the deleted entry; historical deletions made before this release cannot be reconstructed.
- Schema 1 upgrades to schema 2 without deleting rows or reviews. A one-time import repair releases legacy amount/time pairing aliases so available inbox messages can be reconsidered. Old collapsed rows cannot be reconstructed without their original messages; re-import does not guarantee complete repair.
- Review, delete, paste and sample writes run on a bounded background queue. Notification callbacks enqueue processing after checking source/capture settings. Capture failures or queue saturation set a visible Settings warning. Jobs queued before a local reset cannot recreate old data afterward.
- Single-entry edits update one row; group reviews select a bank/account/source/direction scope and update matching rows only. Review mutations use transactions. Broad read-only screens and some settings still perform database reads on the UI thread; this is not a complete asynchronous UI rewrite.
- Budget outflow excludes cash withdrawals and explicitly marked transfers. Overview shows all money out, cash and marked transfers separately. Remaining money out can still include unidentified transfers; it is not verified consumption. The widget and budget insight use the same budget amount.
- Overview now offers monthly message history, previous/next navigation and up to five largest unreviewed entries. It works with unreviewed data and short histories. Samples, receipts and excluded entries do not inflate the summary. Credits are labelled money in, not income. No-message months do not imply zero spending.

## Pilot use

Build and install the project-signed release APK as documented in README. Existing project-signed 0.6.1 installs can update in place; debug-signed installs still require the separate signing transition described there. No automatic uninstall or device operation is performed by this change.

After updating, re-import available SMS if the previous version paired branch alerts. Review large or ambiguous entries and mark only known own-account movements as Transfer. Possible duplicate alerts may remain visible; there is no automatic amount/time guess to remove them.

## Verification

Host tests cover the ATM/UPI collision, repeated notifications, bank-scoped one-to-one copies, distinct network identities with equal arrival time, deletion across reopen/import, schema upgrade, targeted edits, monthly totals and a 20,000-row ledger. Existing migration, capture/import and pilot-contract suites remain in place. Host timings are not handset performance claims.

Signed release assembly, lint, standalone parser/facts/insight/recurrence probes and Node admin/API suites are checked during this change. Real handset lifecycle, OEM capture, background sync and the new dialogs still require field verification.

Remote hosting is unchanged: the pilot backend remains SQLite on one persistent host. Vercel needs a durable external database and eligible hosting plan. No cloud deployment or research-data upload occurs in this release.
