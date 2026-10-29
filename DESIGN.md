# Paynotes design system

The current user brief supersedes earlier Prardhana mockups and the three-tab audit proposal. Paynotes has four destinations: Overview, Activity, Insights, Settings. Onboarding and account creation are later work.

## Research before implementation — 25 October 2026

- Google Fit: Day/Week/Month navigation and tap-through from summary to activity. https://support.google.com/fit/answer/6090183?hl=en
- WHOOP: period arrows, one selected metric, trend inspection and contextual detail. https://support.whoop.com/s/article/Viewing-Trends
- Apple Fitness: daily summary and drill-down to historical activity. https://support.apple.com/en-in/guide/iphone/iph4c34a8a95/27/ios/27
- Dribbble chart references were visually inspected: borrow strong numeric hierarchy and compact range controls from market charts, not trading controls or ornamental candlesticks. https://dribbble.com/search/app-chart
- Mobbin's requested gallery showed only its navigation/sign-in shell; its gated app flows were not inspected. https://mobbin.com/discover/apps/ios/latest

## Design decisions

Remembered benefit: “I can see how my spending is changing and get to the payments behind it.”

Light, precise, restrained. One prominent figure and one chart per overview. No decorative rings, gradients, financial health score, congratulatory spending judgments, or dashboard card mosaics. A dotted previous-period line is context, not a performance target. Changes are neutral: higher spending is not inherently bad.

- Canvas: #F7F9FA; surface: #FFFFFF; ink: #102C34; secondary text: #586A73.
- Primary teal: #087F8C; pale teal: #E3F2F2; line: #DFE7EA. Warning: #9A5417, only for errors or uncertainty.
- Typeface: bundled DM Sans, OFL license. Display 40–44sp, screen title 28sp, section 19sp, body 16sp, secondary 13–14sp, navigation 12sp. Tabular figures for money. System fallback for unsupported scripts.
- Spacing: 4/8/12/16/24/32dp. Screen gutter 24dp. Minimum interactive height 48dp, content grows with font scale. Surface corners 16dp, controls 12dp. No decorative shadows.
- Navigation: persistent bottom bar, icon plus label, selected state with shape and text. Chart remains accessible through previous/next bucket controls and a textual selected value.

## Current screen behavior

Overview: Day/Week/Month, previous/next and date selection, recorded spending, equal-elapsed previous-period comparison, tappable hourly/daily bars, money in/cash/own transfers, category breakdown, recent payments. Empty and historical gaps state “no recorded payments,” never imply complete finances. Chart and category selections open the same filtered Activity. No demo data is inserted into a user's ledger.

Activity: newest first, grouped by date; search; account, transaction type and date filters that combine. Amount and payee lead. Provenance lives in detail. Account identity is issuer plus exact masked account, never suffix-merged.

Insights: conversational interface, practical starters, editable spending target. Local summaries work without an AI service and are explicitly labelled local. AI mode requires configuration and a preview before sharing conversation plus numerical aggregates. No raw SMS, account identifiers, merchant names or references are automatically included. Goals require an explicit user save; chat never silently changes a budget.

Settings: local profile, spending target, Messages & notifications, AI connection, privacy, help, and collapsed pilot tools. Source management shows two clear source rows, one import action and optional advanced range. Notification access is not needed for SMS import. One short disclosure per source leads to the real Android permission UI. Permission denial and partial grants remain recoverable.

## Scope boundaries

Keep Android application ID, data files and signing identity to preserve upgrades. Rename user-facing product strings and icon to Paynotes. Keep extraction/correction semantics from the existing 0.6.3 work. No login, cloud backup, bank account linking, production hosting or physical-phone data operations in this redesign.
