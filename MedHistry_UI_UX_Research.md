# MedHistry — UI/UX Deep Research

**Date:** April 6, 2026
**Tagline:** "Know your patient before they walk in"
**Core USP:** Seamless, dead-simple UI/UX for both patient and doctor

---

## The Product in One Line

Patient uploads medical records → AI generates a structured summary → Patient shows a 6-digit access code (like Google Authenticator) → Doctor enters code → Sees patient history in 30 seconds → Closes it. Done.

---

## Part 1: Patient App — Upload & Health Story

### The Patient's Mindset

Patients approaching a health app are often anxious, not tech-savvy, and skeptical about sharing private data. Research shows 53% of medical app users hesitate due to privacy fears. The key insight: patients don't want to "use an app" — they want to solve a problem (organize their messy paper records, explain their history to a new doctor quickly). Every screen in the patient app should feel like it's solving that problem, not adding work.

The emotional design matters. When patients feel sick or stressed, complex interfaces amplify anxiety. Use calming colors (blues, greens, soft grays), generous whitespace, and reassuring language throughout. Never overwhelm. Never shame.

### Document Upload & OCR Flow

This is the most critical patient interaction — uploading old prescriptions, test results, and referral letters. Get this wrong and patients abandon the app.

**Camera-First, Gallery Fallback:** The primary action should be "Take a photo of your document." Put the camera button front and center. Below it, offer "Choose from gallery" and "Upload PDF" as secondary options. Most Indian patients will photograph paper documents, not upload digital files.

**Guided Photo Capture:** Don't just open the native camera. Build a custom capture screen with alignment guides (a document-shaped overlay), lighting detection (warn if too dark/glare), and auto-edge detection that crops the document automatically. Research shows apps with guided capture get significantly higher OCR accuracy than those using raw camera output. Show a brief animation or tip on first use: "Place document on a flat surface, make sure all edges are visible."

**OCR Processing Feedback:** After capture, show a progress indicator while OCR runs. Don't use a generic spinner — show something meaningful like "Reading your document..." → "Extracting medicine names..." → "Almost done." Add a 150ms pause before showing results even if processing is instant — research shows users trust results more when they see the system "working."

**Post-OCR Review:** Show extracted data with confidence highlighting. Fields the AI is confident about appear normally. Fields with lower confidence appear with an amber highlight and an "Edit" button. Let patients correct mistakes easily with inline editing. For medication names, offer autocomplete from a database of common Indian generic and brand drugs (~3,000 entries covers most cases). This step builds trust — patients see the app isn't just blindly storing gibberish.

**Document Organization:** After upload, documents should appear in a simple timeline view (newest first). Categorize automatically: Prescriptions, Lab Results, Discharge Summaries, Imaging, Other. Let patients add a label or date if the OCR missed it. Keep the interface minimal — a list of cards, each showing document type icon, date, and doctor/hospital name if detected.

**Batch Upload:** Patients often bring a "paper bag of records" — 10-20 documents at once. Support continuous scanning (capture one, immediately ready for next) with a running page counter. Don't force them through a review step after each page — let them scan everything first, then review all at once.

**Error Handling:** When image quality is too poor, show specific guidance: "The photo is blurry — try holding your phone steady" or "The lighting is too dark — move closer to a window." Never show technical errors. Always show what to do differently.

### The Health Story View

Once documents are uploaded and processed, patients see their "Health Story" — their medical history in plain language, not medical jargon.

**Plain Language is Non-Negotiable:** Research shows nearly 90 million Americans (and a comparable proportion in India) struggle with health information comprehension. Every piece of medical data should have a patient-friendly translation. "Hypertension" becomes "High blood pressure." "Dyslipidemia" becomes "Abnormal cholesterol levels." Lab values should show the number AND what it means: "Your blood sugar was 145 mg/dL — this is higher than the healthy range of 70-100."

**Timeline Layout:** Use a vertical timeline as the primary view — reverse chronological (newest at top). Each entry is a card showing: date, doctor/hospital, visit type, and a 1-2 line summary. Tap to expand and see full details (medications prescribed, lab results, doctor's notes in plain language). This mirrors how patients think about their health — as a sequence of events, not categories.

**Health Summary Card (Top of Screen):** Before the timeline, show a summary card with the most important current information: active conditions (in plain language), current medications (with dosage and frequency using simple terms like "1 tablet every morning"), and any pending items ("Your liver test results are expected soon"). This card is essentially what the doctor will also see — so patients can preview their own briefing.

**Lab Results Visualization:** For repeated tests (blood sugar, blood pressure, cholesterol), show a simple trend line. Mark the normal range as a green band, the patient's values as dots on a line. Use traffic-light colors: green (normal), amber (slightly off), red (concerning). Always pair color with text — never rely on color alone (affects colorblind users). Add a one-line interpretation: "Your blood pressure has been improving over the last 3 months."

**Medication Display:** Show current medications as a simple list: drug name, dosage, frequency in plain language ("1 tablet with breakfast"), and why they take it ("for blood pressure"). Show medication history with dosage changes over time as a mini-timeline within each drug's card. This helps both the patient understand their treatment and ensures the AI summary is accurate for the doctor.

### The 6-Digit Access Code (Google Authenticator Style)

This is the consent mechanism — patient controls who sees their data.

**Display Design:** The code should be the most prominent element on a dedicated "Share" screen. Large, high-contrast digits (at least 48px font), well-spaced for easy reading aloud. Use a monospace font for the digits so they're unambiguous (no confusion between 0/O, 1/l). Show a circular countdown timer around or below the code indicating how long until it refreshes.

**Rotation Timing:** Google Authenticator uses 30-second rotation. For MedHistry, consider 60-90 seconds — doctors may need slightly more time to pull out their phone and type the code, especially older doctors who type slowly. The code should grant access for the duration of a consultation session (suggest 15-30 minutes), not just the 60 seconds of the code's validity. Once the doctor enters a valid code, they get a time-limited session.

**Visual Trust Signals:** Below the code, show a brief message: "Show this code to your doctor. They'll be able to see your health summary for this visit only." Add a lock icon and "Your data is encrypted" message. After a doctor accesses the data, show a confirmation: "Dr. [Name] viewed your health summary at [time]." This transparency builds trust.

**Access History (Consent Manager):** In settings or a dedicated section, show a log of every access: who viewed, when, for how long. Let patients see this anytime. This is critical for DPDPA compliance and patient trust. Simple list: "Dr. Vivek Malhotra — April 5, 2026, 10:32 AM — Viewed for 4 minutes."

**What If Patient Doesn't Have Phone?** Consider a fallback for Phase 2: a printed QR code from a previous visit, or an Aadhaar-linked lookup with explicit consent. For Phase 1, the app-based code is sufficient since the target users (Jadeva hospital's 300 patients) will be onboarded with the app.

---

## Part 2: Doctor App — Enter Code, See Summary, Close

### The Doctor's Mindset

Doctors see 60-100 patients daily. They spend 10-15 minutes per consultation. Every second spent on an app is a second stolen from the patient. Research shows 69% of doctors feel overwhelmed by EHR clerical work. The doctor app must be almost invisible — open, code, summary, close. No learning curve, no onboarding tutorial, no feature discovery. Just the data.

87% of doctors already use smartphones during patient care. One-third of clinical searches happen on mobile. But many senior doctors are not tech-savvy. The app must work for a 60-year-old doctor who barely uses WhatsApp, as well as a 30-year-old who lives on their phone.

### The Code Entry Screen

This is the first (and often only) screen the doctor interacts with. It must be frictionless.

**Design:** A single, clean screen with one purpose: enter the 6-digit code. Large input field (or 6 individual digit boxes like OTP screens everyone is used to in India — UPI, banking apps, etc.). Auto-focus on the input field when the app opens so the doctor can start typing immediately. Numeric keypad only. Auto-submit when 6 digits are entered — no "Submit" button needed.

**Speed Target:** From app open to seeing patient summary should take under 10 seconds. App open (1s) → Type 6 digits (3-5s) → Loading (1-2s) → Summary displayed.

**Error States:** If code is invalid: "This code doesn't match any patient. Please check and try again." If code is expired: "This code has expired. Please ask the patient to show a new code." Keep error messages simple, never technical.

**Biometric Login:** Use fingerprint/face unlock instead of username/password for app access. Doctors shouldn't need to log in every time — biometric on app open, then straight to the code entry. Minimize friction ruthlessly.

### The Briefing Card (Patient Summary)

This is the core of the entire product. The doctor enters a valid code and sees the patient's medical history as a structured, scannable briefing card. Not paragraphs. Not AI summaries to read. Structured, glanceable data.

**The "Glance-Grok-Go" Framework:**

**Glance (0-3 seconds) — Without scrolling, the doctor sees:**
- Patient name, age, gender (e.g., "Rajesh Kumar, 52M")
- Critical alerts in a red banner at the very top (e.g., "CRITICAL: Known Penicillin Allergy — Severe reaction")
- Number of active conditions as a quick count

**Grok (3-15 seconds) — Scrolling slightly:**
- Active Conditions: listed as chips/tags (Hypertension, Type 2 Diabetes, Hyperlipidemia)
- Current Medications: each on one line — drug name, dosage, frequency (Amlodipine 5mg OD, Metformin 500mg BD, Ecosprin 75mg OD)
- Last Visit: hospital, doctor, date, one-line summary (City Care Hospital, Dr. Vivek Malhotra, 15 Mar 2026, Routine follow-up, BP 130/80)

**Go (15-30 seconds) — If they need more:**
- Pending Investigations with amber warnings (LFT ordered 18 days ago — no result)
- Tap any section to expand for detailed history
- Previous visits, older lab results, prescription history

**Visual Hierarchy — What Goes Where:**

The critical alerts section MUST be at the very top and always visible without scrolling. Research shows that allergy alerts and drug interaction warnings placed below the fold get missed. Use a red/coral background with white text and a warning icon. This is a safety requirement, not a design preference.

After critical alerts, the information priority is: Active Conditions → Current Medications → Last Visit → Pending Investigations. This matches how doctors think: "What's wrong with this patient? What are they taking? What happened last time? What's outstanding?"

**Typography for Medical Data:**
- Patient name/ID: 20-22px, bold
- Critical alert text: 16-18px, bold, on red background
- Section headers (Active Conditions, Medications): 14-16px, bold
- Drug names: 14px, semibold
- Dosage/frequency: 12-13px, regular
- Dates and metadata: 11px, light gray

Use a clean sans-serif font (Inter, Roboto, or system default). Monospace for dosage numbers for clarity. Always include units with dosages (5mg, not just 5).

**Color System:**
- Critical/Danger: Red (#E74C3C) — allergies, severe interactions, critical lab values
- Warning: Amber (#F39C12) — overdue investigations, moderate interactions, abnormal labs
- Normal: Green (#27AE60) — normal results, confirmed conditions under control
- Neutral/Info: Blue (#3498DB) — general information, labels
- Background: White with light gray (#F8F9FA) card backgrounds

Never use color alone to convey meaning. Always pair with an icon or text label. This is both an accessibility requirement and a practical one — doctors may glance at the screen in bright sunlight or poor lighting.

**Progressive Disclosure:** The briefing card should show the essentials at a glance, with "tap to expand" for details. Collapsed medications show: "Amlodipine 5mg OD". Expanded shows: prescribed by, prescribed date, indication, interaction warnings. This keeps the initial view clean while making full detail available on demand.

**Medication Display Format:**
```
💊 Amlodipine 5mg | Once daily
💊 Metformin 500mg | Twice daily
💊 Ecosprin 75mg | Once daily
```
Use standard abbreviations doctors already know (OD, BD, TDS, QID) alongside plain descriptions. Show drug interaction flags inline with a warning icon if two current medications interact.

**Pending Investigation Alerts:**
```
⚠ LFT (Liver Function Test)
  Ordered 18 days ago — no result received
⚠ HbA1c
  Due for repeat (last done 4 months ago)
```
Show time elapsed since order, expected completion if known, and highlight overdue items in amber. This helps doctors follow up on things that fell through the cracks.

### Closing the Session

After the doctor is done viewing, they simply close the app or navigate away. The session auto-expires after the configured time (15-30 minutes). No "end consultation" button needed — keep it invisible. If the doctor wants to explicitly close access early, a simple "Done" button at the bottom of the briefing card works.

The patient's app should show a notification: "Dr. [Name] finished viewing your summary" with the timestamp. This closes the loop and reinforces trust.

---

## Part 3: Design Principles for Both Apps

### Simplicity as Religion

Every feature request should pass the test: "Does this make the core flow faster or does it add friction?" The core flows are:
- Patient: Upload → See summary → Show code
- Doctor: Enter code → See briefing → Close

Anything that doesn't serve these flows is Phase 2 or later.

### Accessibility

**Font Sizes:** Body text minimum 16px. The 6-digit code should be at least 48px. Section headers 14-16px bold. Touch targets minimum 48x48px with adequate spacing between them.

**Contrast:** 4.5:1 minimum for normal text, 3:1 for large text (WCAG 2.1 AA). Test in bright sunlight conditions — doctors and patients will use these apps in well-lit hospital lobbies.

**For Elderly Users:** Avoid complex gestures (swipes, long-press, pinch). Stick to simple taps. Make buttons large and clearly labeled. Avoid hamburger menus — show navigation directly. Offer a font size toggle accessible from the home screen.

**Screen Reader Support:** Full VoiceOver (iOS) and TalkBack (Android) compatibility. Especially important for the patient app where some users may have visual impairments.

### Color Palette

**Patient App:** Calming and trustworthy. Primary: soft blue (#4A90D9). Accent: teal green (#2ECC71). Background: white with light gray cards. Text: dark charcoal (#333333). This palette reduces anxiety and builds trust.

**Doctor App:** Professional and efficient. Primary: deep blue (#2C3E50). Accent: only for alerts (red for critical, amber for warnings). Background: white. Text: near-black. Minimize decorative color — the doctor app should feel like a clean, professional tool, not a consumer app.

### Indian Market Considerations

**Language:** Start with English. Hindi support for Phase 2. The patient app especially will need multilingual support for scale, but for Jadeva hospital's initial 300 patients, English is sufficient if the plain-language health story is simple enough.

**Network:** Indian hospitals have variable connectivity. The doctor app should cache the briefing card once loaded so it remains viewable even if connectivity drops mid-consultation. The patient app should support offline viewing of their own health story (already downloaded to device).

**Device Diversity:** Many Indian patients use budget Android phones with smaller screens and older OS versions. Test on Android 10+ with 5-inch screens. Don't assume high-end devices.

**UPI-Style OTP Familiarity:** Indian users interact with 6-digit OTPs constantly — UPI payments, bank logins, Aadhaar verification. The MedHistry access code will feel immediately familiar. Lean into this — make the code entry screen look and feel like the OTP screens Indians already know.

### Data Privacy & DPDPA Compliance

**Patient Consent:** Every access must be explicitly initiated by the patient (showing the code). No background data sharing. The code IS the consent.

**Access Logging:** Every doctor access is logged with timestamp, duration, and doctor identity. Patients can view this log anytime.

**Data Storage:** Health data should be encrypted at rest and in transit. Show "Your data is encrypted" visually in the patient app.

**Auto-Expiry:** Doctor access sessions expire automatically. No persistent access. Each visit requires a new code.

**Anonymized Data (Future):** The architecture should support stripping PII for future anonymized data analytics (for pharma companies), but this is Phase 2+. Build the consent infrastructure now so it's ready later.

---

## Part 4: What to Study Before Building

### Apps to Download and Explore

- **Google Authenticator** — Study the code display UX (large digits, countdown timer, rotation)
- **Practo** — Study the doctor profile and health record organization
- **1mg / Tata 1mg** — Study the medicine information display and document upload
- **Adobe Scan / CamScanner** — Study the document scanning capture flow (alignment guides, auto-crop)
- **Apple Health** — Study how health records are displayed to patients in plain language

### Key UX Metrics to Target

- **Upload completion rate:** >85% of users who start an upload should finish it
- **OCR accuracy perceived satisfaction:** >90% of extracted fields correct without manual editing
- **Code entry to summary display:** <10 seconds
- **Doctor time on briefing card:** 30-60 seconds average (this means the summary is working)
- **App crash rate:** <1%
- **App load time:** <2 seconds

### Design Checklist

**Patient App:**
- [ ] Camera-first document upload with alignment guides and auto-crop
- [ ] Batch scanning support (multiple pages without interruption)
- [ ] Post-OCR review with confidence highlighting and inline editing
- [ ] Document timeline (newest first) with automatic categorization
- [ ] Health story in plain language (no medical jargon)
- [ ] Lab result trends with simple visualization (green/amber/red)
- [ ] Current medications with dosage in plain terms
- [ ] 6-digit access code screen (large digits, countdown timer, monospace font)
- [ ] Access history log (who viewed, when, how long)
- [ ] Offline viewing of own health story
- [ ] 16px minimum body text, 48px minimum code digits
- [ ] Calming blue/green color palette

**Doctor App:**
- [ ] Biometric login (fingerprint/face — no password friction)
- [ ] Code entry screen with auto-focus, numeric keypad, auto-submit at 6 digits
- [ ] <10 seconds from app open to briefing card display
- [ ] Critical alerts at top (red banner, always visible without scrolling)
- [ ] Active conditions as scannable tags/chips
- [ ] Medications: drug name, dosage, frequency — one line each
- [ ] Last visit summary (hospital, doctor, date, one-line note)
- [ ] Pending investigations with time-elapsed warnings
- [ ] Progressive disclosure (tap to expand any section)
- [ ] Auto-expire session after 15-30 minutes
- [ ] Cache briefing card for offline viewing once loaded
- [ ] Professional blue/white color palette with red/amber for alerts only

---

*Research compiled from 50+ sources including PMC medical journals, healthcare UX case studies, WCAG accessibility guidelines, DPDPA compliance resources, and real-world app benchmarks (2024-2026).*
