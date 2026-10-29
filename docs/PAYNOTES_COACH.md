# Paynotes 0.8: planning and coaching

## Product loop

Understand personal spending → enter a realistic payday plan → review a goal or limit → explicitly confirm → review actual progress. Voice and text use the same conversation. A model suggestion never writes financial state.

### Available in the app

- Overview starts with a payday plan. It shows flexible spending remaining, next payday, outstanding shared-payment receivables and allocation uncertainty. The existing day/week/month comparison remains below it.
- Insights includes My plan, Goal and Limits. Offline explanations work without a provider. An optional configured AI service receives a previewed conversation and bounded financial context and can return a prefilled goal or category-limit draft.
- Push-to-talk uses the device speech recognizer. Its result becomes an editable draft; the user must tap Send. Android may use an online recognizer despite the offline preference, so the app explains this before opening it. Paynotes stores no audio. Listen uses an installed offline English voice, stops on leaving the app, and has a stop toggle.
- The plan includes monthly take-home pay, payday, fixed commitments, variable essentials, debt payments, irregular costs/buffer, goal allocation and optional valued spending. Payday 29–31 is clamped for shorter months without shifting later months.
- One active savings goal: target amount/date, required weekly contribution and comparison with the entered monthly goal allocation. Contributions and withdrawals are explicitly self-reported; no money is moved. Editing a goal preserves its contribution history.
- Calendar-month category limits show recorded usage. They are guardrails within the plan, not additional money.
- Activity payment details expose personal share, protected-cost allocation, own transfers, card settlements and linked incoming refunds/repayments. Raw bank evidence is preserved. Unknown recipient names do not determine purpose.
- Settings exposes the same plan controls. Plan changes offer one-level undo. Plan/context and payment corrections can be exported through Android's document picker. Delete local data clears the new tables too.

## Financial semantics

All amounts are integer paise. Income is entered by the user, never inferred from total credits.

`flexible allocation = income − fixed − essentials − debt − irregular reserve − monthly goal allocation`

Payments explicitly assigned to protected costs are counted separately. Unassigned spending counts against flexible spending conservatively. Protected-cost spending above the total protected allocation reduces the remaining plan as well.

`remaining plan = flexible allocation − recorded flexible spending − protected-cost overrun`

This is not a bank balance or safe-to-spend guarantee. Expected salary is not verified received money. Cash, missing captures and unrecorded obligations can leave gaps. Negative plans remain visible rather than being clamped to a falsely reassuring zero.

For a ₹2,400 shared dinner with a ₹600 personal share, personal spending is ₹600 and the initial receivable is ₹1,800. A linked ₹1,000 repayment reduces the receivable to ₹800; it does not become salary or available-plan money. Repayments cannot exceed the outstanding shared amount. Refunds reduce the original purchase's personal spending, including in its original historical period; a later refund can therefore revise historical charts. Refunds cannot exceed the original remaining personal share. Mixed refunds for several people's shares require manual review rather than automatic inference.

Own transfers and card settlements have zero personal spending. Only exclude a settlement when the underlying purchases are captured. Payment amount/direction changes that would invalidate confirmed links are rejected until those corrections are explicitly reset. Deleting a payment removes its associated correction links. No automatic recipient-wide purpose rule is learned from one correction.

Under-budget spending is not saved money. Goal progress equals self-reported contributions minus withdrawals. It is neither a verified account balance nor evidence of increased net wealth. Weekly framing always displays the larger monthly commitment in goal review. Past-due goals need a revised date; open the goal editor to replan.

## Implementation

- `MoneyPlan`: exact monetary parsing, payday boundaries, allocations and goal contribution math.
- `CoachStore`: SQLite persistence, bounded read models, correction/link validation, AI context. Schema 3 adds `coach_state` and `payment_meaning`; existing movements survive upgrade.
- `CoachUi`: shared forms, confirmation, undo, progress, purpose and export flows.
- `CoachAnswers`: bounded offline explanations, explicitly labelled rather than masquerading as a general model.
- `CoachProposal`: validates untrusted AI drafts before they can prefill a form.
- `PaynotesData`: corrected personal-spending totals and Activity filter integration.
- `server/insights.cjs`: validates plan context, recomputes remaining-plan consistency, enforces bounded conversation/output, returns explanation plus optional typed draft. Existing authentication, request limits, timeout and `store:false` remain.

The behavioral layer is a coaching policy, not a newly trained or clinically validated model. It protects valued spending, avoids shame/diagnosis and universal savings ratios, suggests one manageable experiment, exposes uncertainty and makes replanning possible. Research rationale is in `PAYNOTES_VALUE_RESEARCH_2026-10-27.md`.

## Runtime setup and limits

Open-ended AI requires the existing HTTPS service deployment with `OPENAI_API_KEY`, `OPENAI_MODEL` and a private `PILOT_ACCESS_TOKEN` of at least 24 characters. Configure its endpoint/token in Settings. Provider keys never belong in the APK. The app previews goal names and spending preferences as well as financial totals before transmission. Offline planning remains functional when the provider is missing or fails.

Speech requires a device speech service; playback requires an offline English voice. Emulator transcript injection tests the app's handling, not real microphone recognition or accent/language accuracy. No phone permission or capture connection is required to build a manual plan.

Current scope is one monthly income plan and one active goal. Category limits use calendar months while the flexible plan follows payday; both are labelled. Linked credits reference one original payment, with the most recent 200 eligible originals offered. Pooled settlements spanning several purchases, household coordination, multiple concurrent goals, irregular-income forecasting, automatic money movement and behavioral reminder experiments remain later work. Export is portable JSON, not a complete restore/backup feature. No model training, production hosting or live-provider quality certification is implied by the build.

## Validation

Host tests cover money precision, payday/leap-year boundaries, protected-cost overruns, partial reimbursements, overpayment rejection, linked refunds, transfers/card settlements, goal funding/withdrawals, invalid proposals, correction invalidation, filtering and schema migration. Server tests cover context validation, calculation mismatch, structured proposals, privacy boundaries, authentication, quotas and provider failure/output validation.

`CoachJourneyChecks` is restricted to a disposable emulator. It exercises actual plan/goal/limit confirmation dialogs, contribution persistence, editable voice results with no automatic sending, local coaching and personal-share correction. Live speech services and live-provider output need separate device/service validation.

Validation result: 53 Android unit tests and 8 AI-server tests pass. Android lint reports 0 errors and 38 warnings (including existing project warnings). The disposable-emulator coaching journey passed. The release build uses the existing project signing key and versionCode 11 / versionName 0.8.0. Provider credentials were absent, so live-model quality and real microphone recognition were not exercised.
