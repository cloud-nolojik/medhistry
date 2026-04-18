# MedHistry Patient App — Final UI/UX Redesign Brief

> **This is the active, approved brief. It supersedes `PATIENT_APP_CHAT_FIRST_REDESIGN.md`** — that earlier brief proposed a chat-first model which we've decided against.

## Context

MedHistry patient app (Kotlin Multiplatform / Compose Multiplatform, modules under `mobile/patientApp` and `mobile/shared`). Reference prototype of the approved design: `patient-app-prototype-v2-dashboard.html` in the repo root. Use that prototype as the visual and interaction source of truth.

## Chosen direction

A **per-person dashboard** model with a prominent **Ask AI** affordance and a prominent **Share with Doctor** affordance. Family-first navigation. Structured data stays front and center. Conversational AI is available as a feature, not the frame.

## Primary design principles

1. **Patient-first voice.** Every label, empty state, error, alert, and suggestion should sound like a kind human talking to a worried family member — not a developer or a clinician. Avoid medical jargon in UI chrome. Use the active person's name whenever possible.
2. **Structured data is the source of truth.** Numbers, ranges, and statuses must be visible without requiring the user to chat.
3. **One active person at a time.** The whole app is scoped to the currently selected family member.
4. **Sharing is event-driven**, not a daily destination. Keep it as a button, not a tab.

## What stays unchanged

- Backend (FastAPI) contracts
- Lab report detail page — the screen is already strong, only copy tweaks needed (see Patient-friendly copy section)
- Per-report "Ask about this report" chatbot
- QR sharing flow mechanics (structured summary payload to doctor)
- Onboarding, OTP, signup, login
- Notifications / Access history logic

## Navigation structure

### Bottom navigation (3 tabs only)

1. **Family** — list of family members
2. **Dashboard** — active person's overview (this is the default landing tab)
3. **Profile** — owner's profile & settings

**Removed from bottom nav:**
- Records (moved into Dashboard as a section + drill-in list screens)
- Medicines (merged into Dashboard's "Currently Taking" + drill-in)
- Lab Results (merged into Dashboard's "Latest Results" + drill-in)
- Share (promoted to a prominent button on Dashboard)

### Floating action button

A **Scan** FAB (circular, primary color, camera icon) is pinned to bottom-right on every primary screen (Dashboard, Family, Records sub-screens). Tapping opens the scan flow pre-scoped to the active person. This replaces the old Upload tab.

### Person switcher

Removed the horizontal chip strip from all screens. Switching now happens in three ways:

1. Tapping the name in the Dashboard header opens a dropdown with the 3-4 most recent members + "Add family member"
2. Tapping a member in the Family tab sets them as active and navigates to Dashboard
3. From Profile → "Family members" → tap a member

## Family tab spec

**Header:** "Family" + subtitle "Manage everyone's health in one place"

**Body:**
- Prominent "+ Add family member" card at the top (dashed border, primary color accent)
- Full-width member cards below, one per family member, sorted with the phone owner first (with a "You" chip), then by most recent activity

Each member card shows:
- Avatar (colored circle with first letter)
- Name + relationship subtitle ("Child · 1 month old", "Spouse", "Mother · 68 years")
- Meta row: document count, alert count (amber if > 0), or "All clear" check (green) if no alerts
- Pending item line if any: "📅 Repeat hormone test tomorrow" (amber text, bold)

Tapping a card sets that person as active globally and navigates to Dashboard.

## Dashboard spec (the main screen)

This is the centerpiece of the app. Layout, top to bottom:

### 1. Header
- Status bar
- Avatar (colored circle with initial) on left
- Name + subtitle (e.g., "induchoodan / Child · 1 month old"). Name is tappable → opens person-switcher dropdown.
- Right side: search icon, overflow (3-dot) menu
- Sticky (stays visible while scrolling)

### 2. "How [name] is doing" card (AI Summary)
- Card title: "How induchoodan is doing" — use the person's actual name, never a generic "Health Summary"
- Optional amber alert banner inside the card if anything needs attention — short sentence like "Most results are fine, but some hormone levels need attention."
- 2-3 sentence plain-language summary generated from latest records
- Small timestamp in corner: "17 Apr"

### 3. Ask AI CTA
- Full-width card, teal gradient background, white text
- Title: "Ask about induchoodan's reports"
- Subtitle: "Get plain-language answers from all records"
- Chat bubble icon on left, chevron on right
- Tapping opens person-scoped chat (see Ask AI section)

### 4. Share with Doctor CTA
- Full-width card, white background, primary-color outline
- Title: "Share with Doctor"
- Subtitle: "Show QR for instant access — expires after visit"
- QR icon on left, chevron on right
- Tapping opens the Share screen pre-scoped to the active person

### 5. Upcoming section
- Section header "Upcoming" with count pill
- Empty state copy: "You're all caught up — follow-ups and repeat tests will show here."
- Populated: card with icon, title ("Repeat hormone estimation"), date ("Tomorrow · 19 Apr · Soon"), quick-action buttons (mark done / snooze / dismiss)

### 6. Currently Taking section
- Section header "Currently Taking" + "View all (N) →" link
- Shows up to 3 active medicines: name, dosing ("5 drops daily · Morning"), "Active" chip
- Empty state: "No active medicines right now."
- Tapping "View all" opens Prescriptions list filtered to Active

### 7. Latest Results section
- Section header "Latest Results" + "View all (N) →" link
- Shows up to 3 most recent lab values: test name, small "date · lab" subtitle, value with color coding, status chip ("Normal" / "High" / "Needs attention")
- Tapping a row opens the relevant lab report detail
- Empty state: "No lab results yet. Scan your first report to see them here."

### 8. Recent Activity section
- Section header "Recent Activity" + "Full timeline →" link
- Shows up to 3 most recent events (lab added, prescription updated, visit)
- Each row: colored icon, date, event title, short description
- Tapping "Full timeline" opens the full Timeline screen

### 9. All Records section
- Section header "All Records"
- 2×2 grid of bucket cards:
  - 🧪 Lab Reports (N reports)
  - 💊 Prescriptions (N items)
  - 🏥 Visits (N visits)
  - 📋 Other Docs (N documents)
- Tapping a bucket opens the corresponding list screen

### 10. Empty-state dashboard (new person with zero records)
Replace sections 5-9 with:
- "How [name] is doing" card with welcome copy: "Welcome. Start by scanning a prescription or lab report. I'll read, organize, and summarize it in plain language."
- Large empty card: "No records yet" + "Scan a prescription or lab report to see your health summary here." + primary CTA "📷 Scan your first report"
- The Ask AI card is hidden or shown as disabled until at least one record exists
- The Share with Doctor card remains (user can still share their profile even with no records)

## Ask AI (person-scoped chat) spec

Accessed from the Ask AI card on Dashboard, or from any lab report's "Ask about this report" (that one is scoped narrower — just that report).

### Screen layout
- Header: back arrow, person avatar + "Ask about [name]" + small subtitle "Powered by your records"
- Disclaimer banner (amber background): "I can help you understand your records, but I'm not a doctor. Always confirm with your physician."
- Chat thread: alternating AI and user bubbles, WhatsApp-style
- Suggested question chips just above the input area (update based on context): "What's changed recently?", "What needs attention?", "Show medicines list"
- Composer: paperclip (for in-chat upload), text input, mic icon (voice input), send button

### AI scope rules
- Access: all of the active person's documents, parsed values, conditions, medicines
- Session memory: last ~10 turns
- Refuse cross-person queries politely: "I can only answer about [active person]. Tap their name at the top to switch to someone else."

## Records sub-screens (drill-in from Dashboard)

All three follow the same list pattern — filter chips at top, date-grouped entries, tap to open detail.

### Lab Reports list (`/records/labs`)
- Header: back arrow, "Lab Reports", subtitle "[name] · N reports"
- Filter chips: All / Hormone / Blood / Imaging (or whatever categories exist)
- Date groups with month-year headers ("April 2026")
- Each entry card: icon, title (panel name), lab + doctor, status line (amber for "N values need attention", green for "All normal"), count + date

### Prescriptions list (`/records/prescriptions`)
- Header: back arrow, "Prescriptions", subtitle "[name] · N prescriptions"
- Filter chips: All / Active / Past
- Grouped: "Currently Active" first, then "Past Prescriptions"
- Each entry: icon, medicine name, "Prescribed by Dr. X", status line (active with dosing, or "Completed · date")

### Timeline (`/timeline`)
- Header: back arrow, "Timeline", subtitle "[name] · Complete history"
- Filter chips: All / 🧪 Labs / 💊 Prescriptions / 🏥 Visits
- Grouped by month-year
- Each entry: icon, date, event title, short description

## Share with Doctor screen

- Header: back arrow, "Share with Doctor", subtitle "Let a doctor scan this QR to see [name]'s summary"
- QR code center-screen (large, ~220px)
- Below QR: timer caption "New QR in 58s · Access expires after visit"
- Consent summary box: "What your doctor will see" with bullets (active conditions, current medicines, recent lab values, allergies & alerts)
- Footer note: "🔒 Data encrypted. Doctor cannot save or re-share."

No bottom nav active for this screen — it's a sub-screen of Dashboard. Back arrow returns to Dashboard.

## Scan flow

- Triggered by FAB from any primary screen
- Header: back arrow, "Scan Report"
- "Scanning for" card at top showing the active person (tappable to switch)
- Large dashed camera target: "Take a Photo — Point your camera at the document"
- Three alternate sources: Gallery / PDF File / Batch
- On capture: immediately show processing state, send to backend, return to the active screen with a toast ("Processing your report — we'll update you in ~30 seconds")
- When processing completes: toast/notification "[name]'s lab report is ready — 2 values need attention"

## Patient-friendly copy guidelines

**This is the single most important section of this brief. Apply these rules to every string in the app.**

### Voice rules
- Speak to a worried family member, not a developer
- Use the person's name wherever possible, not "the patient" or "the user"
- Short sentences. Active voice. Everyday words.
- Never say "error" or "failed" — describe what happened and what to do next
- Never use medical abbreviations unprefixed (FSH, LFT, LH) in UI chrome — spell out or explain inline
- Indian English spelling is fine; avoid overly American phrasing

### String replacement examples (apply this pattern everywhere)

| Bad | Good |
|---|---|
| "Health Summary" | "How induchoodan is doing" |
| "Upload Document" | "Scan a report" |
| "Submit" | "Save" or "Done" |
| "API Error" | "Having trouble connecting. Please try again." |
| "OCR Failed" | "I couldn't read this clearly — try another photo?" |
| "Authentication error" | "Let's sign you in again" |
| "No data" | "Nothing here yet. Scan your first report to get started." |
| "Abnormal" | "Needs attention" or "Worth discussing with your doctor" |
| "Non-critical" | "Looks fine" |
| "Delete" | "Remove" (less aggressive) |
| "Critical alert" | "Important" |
| "Dismiss" | "Got it" |

### Empty states — always include 3 parts
1. A warm one-liner about what this section *will* show ("No lab results yet")
2. A gentle explanation ("Scan a lab report and we'll lay out your results in plain language.")
3. A primary action button ("📷 Scan a lab report")

### Error states — always include 3 parts
1. What happened, in plain words ("We couldn't save your scan.")
2. A likely reason, if known ("Looks like the internet dropped.")
3. What to do ("Try again" button, with retry behavior)

### AI chat responses
- Open with empathy when context warrants: "It's understandable to feel anxious, but…"
- Always include: reassurance where appropriate, what the numbers mean, concrete next step (often "discuss with your doctor")
- Never give a definitive diagnosis or prescribe. Always refer to the doctor for decisions.
- Length: 3-5 sentences per response. Break longer responses into shorter paragraphs.

### Microcopy around medical values
- "Total Testosterone: 200 ng/dL (slightly high)" — add parenthetical interpretation
- "Normal range: 0.08 – 0.50" — always show the range
- Plain-language description under every value: "FSH plays a role in sexual development. induchoodan's level is normal."
- Color coding: green (normal), amber (worth watching / discuss with doctor), red (action needed, rare)

## Data model (minimal additions)

The Dashboard can be computed from existing parsed records. Only new state needed:

```
ActivePerson { personId } — app-wide singleton, persisted
RecentSwitches [personId...] — last 3-4 used, for switcher dropdown
```

Chat memory for Ask AI can be session-local for MVP (wiped on app restart). Persist per-report chat threads only.

## Migration strategy

Existing users:
1. On app upgrade, default active person = owner (Vijesh-equivalent)
2. Existing records already keyed to family members — map straight into Dashboard sections
3. Ensure Upcoming is populated from existing follow-up parse output
4. No data migration needed — this is a UI-only change

## Out of scope (don't build in this pass)

- Voice input (stub the mic icon — disable or show a "coming soon" toast)
- Multi-language UI (plan for it: externalize all strings to resource files now)
- Caregiver/permission roles
- Offline mode for chat
- Notification model overhaul

## Acceptance criteria

1. Bottom nav has exactly 3 tabs: Family, Dashboard, Profile
2. Share is NOT a bottom tab; it's a prominent button on Dashboard and reaches the Share screen as a sub-page with a back arrow
3. Scan FAB is present and working on Dashboard, Family, and all Records sub-screens
4. Dashboard for a populated person shows all sections in the order specified (Summary → Ask AI → Share → Upcoming → Currently Taking → Latest Results → Recent Activity → All Records)
5. Dashboard for a person with zero records shows the welcome/empty state (no Latest Results, no Currently Taking sections)
6. Ask AI card opens a person-scoped chatbot with disclaimer, chat thread, suggested chips, and composer
7. Tapping the name in the Dashboard header opens a person-switcher dropdown with recent members + Add
8. Family tab lists all members with stats; Add Member flow works end-to-end
9. Lab Reports list, Prescriptions list, and Timeline are reachable from Dashboard sections and support filter chips
10. Lab report detail page is unchanged from current build (only copy tweaked per guidelines)
11. All strings in the app follow the patient-friendly copy guidelines (run through the replacement table as part of QA)
12. No references to "Records", "Medicines", or "Lab Results" as bottom-tab labels anywhere in the codebase

## Implementation order (suggested)

1. Set up ActivePerson global state + person-switcher dropdown component
2. Rebuild Family tab with full-width member cards
3. Rebuild Dashboard with all sections (start with populated state, then empty state)
4. Wire Ask AI card to a new person-scoped chat screen
5. Wire Share with Doctor button to existing Share screen (add back arrow, remove bottom nav active state)
6. Rewire existing Records / Medicines / Lab Results screens as sub-screens of Dashboard (Lab Reports list, Prescriptions list, Timeline)
7. Add the Scan FAB on all primary screens; remove the old Upload tab/screen entry
8. Remove Records / Medicines / Lab Results / Share from bottom nav everywhere
9. Copy pass: go through every string and apply the patient-friendly guidelines
10. Polish: animations, empty states, loading states, error states

## Open questions (answer before coding)

1. **Voice input** — stub with "coming soon" toast, or implement platform STT (Android SpeechRecognizer / iOS Speech) in this pass?
2. **Ask AI chat history** — session-local (wiped on restart) or persisted per-person? Recommendation: session-local for MVP, revisit once usage is clearer.
3. **Per-report "Ask about this report" chatbot** — keep as separate scoped chat, or merge into person-level Ask AI? Recommendation: keep separate. Different scope, different usefulness.
4. **Language strings** — externalize all to resource files in this pass (so i18n is easier later) or defer?
5. **Notifications copy** — do we update push notification text to match the patient-friendly guidelines? Recommendation: yes, in the same pass.
6. **Active-condition chips on Dashboard** — the current build shows them; do we keep them on the new Dashboard? (Not in the prototype — worth deciding.)

## Reference

- Interactive prototype: `patient-app-prototype-v2-dashboard.html`
- Superseded brief: `PATIENT_APP_CHAT_FIRST_REDESIGN.md` (do not use)
- Current screenshots of the live build: provided by Vijesh on 2026-04-18
