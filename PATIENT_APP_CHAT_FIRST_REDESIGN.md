# MedHistry Patient App — Chat-First Per-Person Redesign

## Context

This is a brief for an AI coding assistant working on the MedHistry patient app (Kotlin Multiplatform / Compose Multiplatform, mobile module under `mobile/patientApp` and `mobile/shared`).

The current app has a 4-tab bottom nav (Home, Records, Medicines, Lab Results) with a horizontal family-member chip strip on every screen. We are pivoting to a **chat-first, per-person experience** where each family member has their own conversational thread that doubles as their timeline.

## Goal

Replace the multi-tab structured UI with a chat-thread Home per person. Upload, history, AI summaries, and Q&A all live in one scrollable conversation. Structured detail pages (lab report detail, prescription detail) are preserved as drill-in destinations from chat cards.

## What stays unchanged

- Backend (FastAPI) contracts — no schema changes required
- Lab report detail page — keep exactly as is (it's the strongest screen)
- Per-report "Ask about this report" chatbot — keep as is, scoped to the report
- QR sharing flow — doctor-facing share remains structured summary, not chat
- Onboarding, OTP, signup, login — no changes
- Profile screen — no changes

## What changes

### 1. Bottom navigation

Replace current 4 tabs with:

- **Family** (left)
- **Chat** (center, larger — current active person's thread)
- **Share** (QR)
- **Profile** (right)

Remove from bottom nav: Records, Medicines, Lab Results.

The center "Chat" tab is the new Home. It always shows the conversation for whichever family member is currently active.

### 2. Family tab

Vertical list of family member cards. Each card shows:

- Avatar + name (e.g., induchoodan)
- Last activity date ("Last update: 17 Apr")
- Alert badge count (e.g., "2 values need attention")
- Document count ("8 documents")
- Pending follow-up if any ("Repeat hormone test tomorrow")

Top of the list: "+ Add Family Member" CTA (full-width card).

Tapping a member card sets them as the active person globally and navigates to the Chat tab.

Remove the horizontal chip strip from all other screens — Family tab is now the canonical switcher.

### 3. Chat tab (new Home, per-person)

Layout, top to bottom:

**Sticky header (top)**
- Back-arrow-style person avatar + name ("induchoodan") on left
- Tap on the name opens a quick-switch dropdown of recent family members + Add Member option
- 3-dot menu on right for: View profile, Show QR for this person, Export

**Health summary strip (sticky, just under header)**
- 2 lines max
- Line 1: active conditions as chips (or "No active conditions" for empty state)
- Line 2: nearest pending alert ("Repeat hormone test tomorrow") OR "All caught up"
- Tap to expand into full summary view (modal or bottom sheet)

**Chat thread (scrollable, fills middle)**
- Reverse-chronological by default (newest at bottom, like WhatsApp)
- Three message types:
  1. **User upload bubble**: shows thumbnail of scanned document + filename + timestamp
  2. **AI processing/summary bubble** (system, left-aligned): "Processed lab report from DDRC Agilus, 17 April. 8 results, 2 values need attention. [View full report →]"
  3. **Q&A bubbles**: user questions (right-aligned) + AI responses (left-aligned)
- "View full report →" link opens existing lab report detail screen
- Long-press any message: copy, delete, share to doctor
- Date separators between days ("Today", "Yesterday", "17 April 2026")

**Composer (sticky, bottom)**
- Paperclip icon (left): opens action sheet with Camera / Gallery / PDF / Batch Scan
- Text input (center): "Ask about induchoodan's health…"
- Mic icon (right of input): voice input — important for low-literacy users
- Send button (right): sends question to AI scoped to this person's full record set
- When composer is focused, suggested-question chips appear above it: "What changed recently?", "What needs attention?", "Show medicines list", "Recent lab results"

### 4. Empty state for a new family member

When a family member has zero records, the chat shows a single welcome message from the AI:

> Hi! I'm your health assistant for [Name]. Tap the camera below to scan their first prescription or lab report — I'll organize everything and answer questions in plain language.

Composer remains active. No structured empty-state cards needed — the chat is the empty state.

### 5. Upload flow (in-chat)

Tapping the paperclip → action sheet with Camera / Gallery / PDF / Batch Scan.

After capture/selection:

1. Immediately show user upload bubble in chat with thumbnail + "Processing…" indicator
2. Send to backend OCR + AI extraction (existing pipeline)
3. Replace processing bubble with completed AI summary bubble linking to structured detail page
4. If multiple documents in one batch, show as a grouped bubble with count

Do NOT show a separate Upload screen. Upload only happens from the chat composer.

### 6. Cross-person Q&A scope

Questions in the person's chat are scoped to that person's records only. The AI should:
- Have access to all of the active person's documents, parsed values, conditions, and meds
- Maintain conversation memory within a session (last ~10 turns)
- Refuse to answer cross-person questions ("Tell me about Vijesh") and suggest switching person instead

### 7. Records timeline (clean, filterable view)

The chat thread is the *conversational* timeline — but users also need a clean chronological list of just their records without Q&A noise. Provide this via a dedicated Timeline view, reachable in three ways (all go to the same screen):

1. "Timeline" icon/button in the chat header (top-right, next to search)
2. Tap the document count pill on a Family member card ("8 documents" → Timeline for that person)
3. Tap the sticky health summary strip → opens summary sheet → "View full timeline" link

The Timeline screen itself:
- Scoped to the currently active person
- Grouped by date (month/year headers)
- Shows only record-type entries: lab reports, prescriptions, visits (no Q&A chat messages)
- Filter chips at top: All / Prescriptions / Lab Reports / Visits
- Date range picker accessible from a filter icon
- Each entry is a card with: icon (rx/lab/visit), title, hospital + doctor, date, AI summary snippet, tap → opens existing detail page
- "Back" returns to chat

This is essentially your current Records tab, preserved — but now reached from chat/family context rather than as a bottom nav item. Keep the existing Records screen code and rewire how it's entered.

### 8. Search inside chat

Add a search icon in the chat header. Tapping opens a search overlay:
- Free-text search across messages, document names, parsed values
- Filter chips: Prescriptions / Labs / Visits / Questions
- Date range picker
- Tap a result to jump to that message in the thread

This is essential because chat threads will get very long over months. Search is different from Timeline: Timeline is "show me my records cleanly", Search is "find a specific thing".

### 9. Doctor share (no change to UX, but data source changes)

QR-share continues to produce a structured summary view for the doctor (active conditions, current meds, recent labs). Source of truth remains the parsed structured data, not the chat thread. Doctors should never see the patient's chat thread.

### 10. "Me" handling

The phone owner (Vijesh in the screenshots) is the default active person. On first launch after this change, Vijesh's chat opens automatically. They appear as the first card in Family tab with a "You" badge.

## Data model implications

No new backend tables required. Chat messages can be stored client-side in a local Room/SQLDelight DB with sync to backend optional for cross-device:

```
ChatMessage {
  id, personId, type (USER_UPLOAD | AI_SUMMARY | USER_QUESTION | AI_RESPONSE),
  timestamp, content (text or doc reference),
  linkedRecordId (nullable, for AI_SUMMARY bubbles linking to lab reports)
}
```

For each existing parsed document, generate a corresponding AI_SUMMARY ChatMessage on first migration so existing users see their history as a chat thread.

## Migration for existing users

On app upgrade:
1. For each family member's existing documents, create AI_SUMMARY chat messages in chronological order
2. Generate a one-time welcome message: "Welcome to the new MedHistry. Your records are now organized as a conversation. Scroll up to see your history, or ask me anything below."
3. Preserve all structured data — only the entry surface changes

## Out of scope (do not build now)

- Voice playback of AI responses (text-to-speech)
- Multi-language UI (separate effort)
- Caregiver/permission roles
- Offline mode for chat (assume online for MVP of this redesign)
- Chat-to-doctor messaging (this is patient ↔ AI only)

## Acceptance criteria

1. Family tab lists all members with stats; Add Member works
2. Tapping a member switches active context and opens their chat
3. Chat composer accepts text, voice (placeholder OK if STT not ready), and document uploads via paperclip
4. Uploads appear as bubbles, get processed, and produce linked AI summary bubbles
5. Tapping "View full report →" on an AI summary bubble opens the existing lab report detail page unchanged
6. Sticky health summary strip updates as new records are added
7. Empty state for new family member shows welcome message and active composer
8. Search in chat returns matches with filter chips working
9. Bottom nav has exactly 4 items: Family, Chat, Share, Profile
10. No more Records / Medicines / Lab Results bottom tabs anywhere
11. Existing structured screens (lab report detail, QR share) unchanged
12. Migration creates chat messages from existing documents on first launch

## Implementation order (suggested)

1. Family tab + member list + active-person state management
2. Chat tab skeleton with sticky header, summary strip, empty composer
3. Chat thread rendering for AI_SUMMARY messages from existing data (read-only)
4. Composer: paperclip → upload flow → bubble lifecycle
5. Composer: text Q&A with AI scoped to active person
6. Rewire existing Records screen as the Timeline view, reachable from chat header + family card + summary strip (no code changes to the screen itself, just entry points)
7. Search in chat
8. Migration job for existing users
9. Remove old bottom tabs, polish

## Open questions to flag back to product (Vijesh)

1. Should chat history sync across devices (backend storage) or stay device-local for MVP?
2. Voice input — use platform STT (Android SpeechRecognizer / iOS Speech) for MVP or wait for cloud STT?
3. When a doctor scans the QR, should access logs appear as a system message in the patient's chat? ("Dr. Vivek Malhotra viewed your summary at 10:32 AM.")
4. Should the per-report "Ask about this report" chatbot be merged into the person-level chat, or kept separate? Recommendation: keep separate — different context scope.
5. Notification model — when AI processes a new upload while app is closed, do we push notify? ("induchoodan's lab report is ready — 2 values need attention")
