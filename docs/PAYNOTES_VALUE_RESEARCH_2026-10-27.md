# Paynotes: user value research and product hypotheses

Research date: 27 October 2026. This is a brainstorm and evidence memo, not an implementation specification. No application behavior was changed for this research.

## Research scope and limits

Read selected Reddit discussions and comments, visible Google Play reviews for Axio, Wallet and Realbyte Money Manager, competitor documentation, and primary behavioral research. This is a qualitative convenience sample. It does not establish complaint prevalence, represent all customers, or prove a market gap. Reviews describe experiences with particular versions; several discussions also contain satisfied users and workable existing solutions. Founder/promotional posts were not used as independent evidence of demand. Published savings experiments are mostly outside India; transferring their effects to this audience is a hypothesis.

## Evidence → opportunity

| Evidence inspected | Interpretation for Paynotes |
|---|---|
| An August 2026 Axio Play review reports missing bank transactions even after rescan. [Source](https://play.google.com/store/apps/details?id=com.daamitt.walnut.app) | Capture reliability and visible gaps are prerequisites for coaching. A lower total cannot automatically mean improvement. |
| An August 2026 Wallet review describes extensive category repair. The developer points to rules; another reviewer likes the app but finds a simple template task hard to discover. [Source](https://play.google.com/store/apps/details?hl=en-US&id=com.droid4you.application.wallet) | Measure correction effort. Learning user-approved rules and understandable defaults matter more than claiming automatic categorisation. |
| A YNAB user describes repeatedly reconstructing partial reimbursements and falling behind. [Source](https://www.reddit.com/r/ynab/comments/1bu6rqq/how_to_handle_never_ending_partial_reimbursements/) | Model personal share, cash outflow and outstanding repayment separately. |
| Indian users discuss split expenses, refund-linking workarounds, privacy and unwanted credit promotion. [Source](https://www.reddit.com/r/IndiaInvestments/comments/1ad24wn/suggest_best_app_to_track_expenses_and_categorize/) | Local handling and explicit transaction relationships are useful hypotheses. Do not interpret anecdotal spam reports as proof of data sharing. |
| One Indian app-seeker explicitly rejects SMS access and wants desktop entry. [Source](https://www.reddit.com/r/personalfinanceindia/comments/1ktpyw7/suggestion_for_expense_tracking_app/) | SMS-based automation is a segment choice, not a universal preference. Preserve manual entry and portability. |
| A budgeting-anxiety thread contains both overwhelmed users and users who find a plan liberating. [Source](https://www.reddit.com/r/ynab/comments/1oc8hsf/budgeting_apps_stress_me_out_rather_than_helping/) | Offer adjustable detail and user-defined priorities. Neither avoidance nor constant monitoring should be imposed on everybody. |
| Realbyte's visible reviews include enthusiasm for manual entry and complaints about paid-access restoration/carryover. [Source](https://play.google.com/store/apps/details/Money_Manager_Expense_Budget?hl=en&id=com.realbyteapps.moneymanagerfree) | Automation alone is not the value proposition. Continuity, comprehensible balances and restoring access matter too. |

## Recommended initial audience and promise

Hypothesis: Android users in India with a reasonably predictable salary, frequent UPI payments, multiple payment sources and occasional shared expenses, who abandon detailed budgeting because reconciliation becomes work. Validate this audience before expanding to households, freelancers and investment tracking.

Promise to test: **Understand what you really spent, see what your plan leaves available until payday, and make one manageable improvement toward a goal.**

The current application remains an expense summary with a chat surface. Its cloud facts contain aggregate movements and a target, not a confirmed income schedule, personal shares, commitments or goal contributions. Better prompting alone cannot supply these missing facts.

## First value layer: explain the movement

Separate counterparty from purpose. A personal name or UPI handle does not establish whether a payment was lunch, rent, a gift, family support, a loan or repayment. A merchant name does not reveal every item purchased. Do not silently infer emotional state or affordability from a category.

Proposed structured records: movement evidence; counterparty alias; purpose; user share; relationship to another movement; confirmation source; confidence; outstanding amount. Preserve the original transaction and audit trail. Allocations sum to the original amount; matching a repayment does not create new income. Same-amount/time matches are suggestions, never sufficient evidence by themselves.

Example: a user pays ₹2,400 for dinner and confirms a ₹600 personal share. Show cash paid ₹2,400, own spending ₹600 and reimbursement expected ₹1,800. Keep the cash unavailable until repayments arrive. Support partial repayments, refunds across periods, cancellations and mistaken matches with undo.

Only verified own-account transfers should be excluded as internal movements. A gift or family-support payment is a real outflow. Card purchases and card-bill settlement require separate spending and cash-flow treatment so neither gets double-counted.

Ask a short contextual question for consequential unknowns, with Skip. Offer a weekly queue capped initially at three useful clarifications. Let users explicitly choose whether a correction applies to this payment or future payments to an exact alias. Mixed-use merchants and people must not inherit an unconditional purpose.

## Second value layer: a realistic payday plan

Collect confirmed take-home income, pay date, fixed commitments, essential variable costs, debt payments, irregular-expense provisions, an optional goal and a cash buffer. Separate expected income from money received. Start with broad commitments/essentials/flexible spending/goal buckets; expose categories as optional detail.

Illustrative month-start plan, not a recommended allocation: ₹60,000 take-home minus ₹25,000 fixed costs, ₹12,000 essentials, ₹5,000 debt payments, ₹3,000 irregular-cost reserve and ₹5,000 goal contribution leaves ₹10,000 flexible spending. The system should detect infeasible goals and offer a smaller contribution or later deadline instead of praising an impossible plan.

Call the result **left in your spending plan**. A credible current-cash estimate additionally needs current available funds, unpaid commitments, pending obligations and data freshness. SMS capture alone cannot support an unconditional “safe to spend” number. Money owed by friends and expected salary remain unavailable until received. Variable-income support needs conservative scenarios and must not count every credit as earnings.

## Third value layer: behavior and small savings

Small framing can help initiation: a field experiment found higher enrollment when equivalent recurring savings were presented daily rather than monthly. This is enrollment evidence in a particular context, not a guarantee of better financial outcomes. Show the full commitment as prominently as the smaller unit. [Hershfield, Shu and Benartzi](https://pubsonline.informs.org/doi/10.1287/mksc.2019.1177)

Reminder effects can be small: a large US bank experiment's best email campaign increased monthly one-time-deposit probability by 0.13 percentage points. Treat reminders as an optional support, with cadence testing and an easy off switch. [2025 megastudy](https://pubmed.ncbi.nlm.nih.gov/41030290/)

Proposed loop: pick one meaningful goal → agree an affordable weekly contribution → protect essentials → review once a week → confirm what was actually set aside. A ₹6,000 goal over 12 weeks is ₹500/week; the user may choose a different amount/deadline. Do not count a proposed transfer, an unspent allowance or a round-up animation as actual savings. Goal-account inflows minus withdrawals show goal funding, not automatically increased net wealth.

A useful prompt: “You are ₹300 under this week's flexible plan. Keep it as a buffer or add it to your goal?” Only offer this after checking upcoming commitments; the user can decline. Round-ups can be an optional capped mechanism later, but should not make spending itself the activity rewarded by the product.

Values-based coaching hypothesis: once weekly, optionally ask whether one or two purchases felt worthwhile. Learn the user's preferences explicitly. Protect valued spending; suggest one experiment for spending the user wants to change. Do not moralise about coffee, family support or entertainment. Avoid punishment streaks, peer spending rankings and automatically escalating targets.

## What “trained on money psychology” should mean

Start with reviewed behavioral principles, deterministic money calculations and a user-editable preferences model. Let the language model explain evidence, ask clarifying questions and present alternatives. It must not invent transactions, choose affordability or move money. Fine-tuning comes later only if consented, curated interaction data and an evaluation show a specific quality gap.

Evaluate scenarios such as irregular income, a negative available balance, shared payments, family commitments, unexpected medical costs, mistaken salary classification, goal withdrawal and incomplete capture. Desired behavior is often abstaining, clarifying or reducing the goal. Optimise for security and choice, consistent with the CFPB's well-being framing; do not transplant its US population norms into an Indian score. [CFPB](https://www.consumerfinance.gov/consumer-tools/educator-tool/financial-well-being-resources/)

## Phase 1: Core Engine & Essential UI

Prioritise transaction relationships/personal share, reliable corrections, confirmed income/payday and one broad spending plan. Add user-controlled export and an encrypted restore path before users accumulate irreplaceable history. Overview should answer plan remaining, commitments and important uncertainties; existing comparison charts support those answers. Activity owns the evidence and splits. Chat explains decisions without becoming mandatory for basic tasks.

## Phase 2: Refinement & Error Handling

Test one weekly goal contribution and one weekly review; add partial reimbursements, changed pay dates, missing records, revocations, negative balances and goal withdrawals. Distinguish observed changes from caused savings. Notifications remain opt-in and user-timed. Preserve manual entry and the ability to correct/undo every inference.

## Phase 3: Post-MVP Backlog

Defer automatic money movement, round-up debits, investment recommendations, household coordination, broad conversational agents, psychological profiles, social comparisons, scores and deeper analytics until the foundational loop earns repeat use. A spending-coach business should not depend on nudging users into more credit.

## Validation proposal

Recruit 12–15 matching users for two pay cycles, with permission to examine only the transaction examples they choose to share. This discovers usability and direction; it is not a powered causal savings study. First ask them to reconstruct a recent confusing payment, abandoned budget and savings attempt before showing concepts.

Compare three jobs: clarifying personal spending, planning until payday and sustaining one small goal. Track correction minutes, known capture gaps, false transfer/salary classifications, whether users can explain the plan, voluntary weekly return, explicitly confirmed goal funding and withdrawals, and perceived control versus stress. Ask what they would stop using if Paynotes disappeared. Avoid daily opens or chat volume as success proxies.

Proposed decision gate: users should obtain a useful first answer with minimal setup and repeatedly make a decision from it without researcher reminders. If reconciliation remains burdensome, fix the ledger before adding coaching. If users like summaries but never act on them, test the decision flow rather than adding more charts. Later use a sufficiently sized controlled comparison to test incremental savings effects.
