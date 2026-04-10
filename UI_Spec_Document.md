# MedHistry — UI Specification Document

**For Figma / Production Implementation (Kotlin Multiplatform)**
**Last Updated:** April 6, 2026

---

## Design System

### Color Tokens

**Patient App**
| Token | Hex | Usage |
|-------|-----|-------|
| primary | #4A90D9 | Buttons, links, active nav, summary card gradient start |
| primary-dark | #3A7BC8 | Pressed states, summary card gradient end |
| primary-light | #E8F1FB | Icon backgrounds, hover states |
| accent | #2ECC71 | Success indicators, normal lab values |
| accent-dark | #27AE60 | Success text on light background |
| danger | #E74C3C | Critical alerts, high lab values |
| warning | #F39C12 | Pending items, amber confidence, overdue |
| text | #1A1A2E | Primary text |
| text-secondary | #6B7280 | Secondary text, descriptions |
| text-light | #9CA3AF | Timestamps, metadata, inactive nav |
| bg | #F8FAFC | Screen background |
| white | #FFFFFF | Cards, input backgrounds |
| border | #E5E7EB | Card borders, dividers |

**Doctor App**
| Token | Hex | Usage |
|-------|-----|-------|
| primary | #2C3E50 | CTA gradient, logo, Done button, splash gradient |
| primary-dark | #1A2530 | Pressed states, gradient end |
| primary-light | #EBF0F5 | Hover states, backgrounds |
| accent | #4A90D9 | Active nav, links, code focus, invite box focus |
| accent-dark | #3A7BC8 | Accent pressed states |
| accent-light | #E8F1FB | Code input focus bg, count badges, info banners |
| danger | #E74C3C | Critical banner background, allergy alerts |
| danger-light | #FEF2F2 | Error feedback bg, privacy icon bg |
| warning | #F39C12 | Pending investigation borders |
| warning-light | #FEF3C7 | Pending investigation backgrounds, notification icon bg |
| success | #27AE60 | Normal lab values, session active badge, hospital verified |
| success-light | #F0FDF4 | Session badge bg, ended icon bg, verified banner bg |

### Typography

**Font Family:** System default stack
- iOS: SF Pro Text / SF Pro Display
- Android: Roboto
- Monospace (for codes/digits): SF Mono / Roboto Mono

**Type Scale**

| Element | Size | Weight | Line Height | Letter Spacing |
|---------|------|--------|-------------|----------------|
| Screen title (H1) | 28px | Bold 700 | 1.2 | -0.3px |
| Large title (H1 alt) | 26px | Bold 700 | 1.2 | -0.2px |
| Section header (H2) | 18px | Bold 700 | 1.3 | 0 |
| Sub-header | 16px | Semibold 600 | 1.3 | 0 |
| Body | 15px | Regular 400 | 1.5 | 0 |
| Body small | 14px | Regular 400 | 1.5 | 0 |
| Caption | 13px | Regular 400 | 1.4 | 0 |
| Label (uppercase) | 12px | Bold 700 | 1.3 | 0.8px |
| Timestamp | 12px | Regular 400 | 1.4 | 0 |
| Tiny | 11px | Medium 500 | 1.3 | 0 |
| QR code container | 200x200px min | — | — | — |
| QR timer text | 14px | Semibold 600 | 1.3 | 0 |
| Invite code input | 22px | Bold 700 | 1.0 | 0 |
| OTP input | 24px | Bold 700 | 1.0 | 0 |
| Patient name (briefing) | 22px | Bold 700 | 1.2 | -0.2px |
| Dashboard CTA title | 20px | Bold 700 | 1.2 | 0 |
| Onboarding title | 24px | Bold 700 | 1.3 | 0 |

### Spacing

| Token | Value | Usage |
|-------|-------|-------|
| xs | 4px | Inline gaps, line spacing |
| sm | 8px | Between label and value, within components |
| md | 12px | Between list items, card internal padding small |
| lg | 16px | Between cards, section body padding |
| xl | 20px | Card padding, content area horizontal padding |
| 2xl | 24px | Screen horizontal padding, between major sections |
| 3xl | 32px | Large gaps (above buttons, between visual groups) |
| 4xl | 40px | Major screen sections (onboarding content) |

### Border Radius

| Token | Value | Usage |
|-------|-------|-------|
| xs | 8px | Small badges, alerts, inline elements |
| sm | 12px | Cards, inputs, smaller components |
| md | 16px | Main cards, buttons, code input boxes |
| lg | 20px | Condition chips, tags, invite code badge |
| xl | 24px | Pill shapes, onboarding icons |
| xxl | 28px | Large onboarding icon containers |
| full | 50% | Avatars, circular elements, profile picture |
| phone | 44px | Phone frame (for prototype only) |

### Shadows

| Token | Value |
|-------|-------|
| shadow-sm | 0 1px 3px rgba(0,0,0,0.08) |
| shadow-md | 0 4px 12px rgba(0,0,0,0.1) |
| shadow-primary | 0 4px 16px rgba(74,144,217,0.3) |
| shadow-doctor | 0 4px 16px rgba(44,62,80,0.3) |

### Touch Targets

All interactive elements: minimum **48x48px** tap area
QR code display: **200x200px minimum** (patient app)
QR scanner viewfinder: **full width, 1:1 aspect ratio** (doctor app)
Bottom nav items: **48x48px** icon area + label
Settings items: **full width x 56px minimum**
Invite code boxes: **44x56px**

---

## Patient App — Screen Specifications

### Screen 1: Splash

**Layout:** Full screen centered, gradient background
**Background:** linear-gradient(135deg, #4A90D9, #3A7BC8)

| Element | Spec |
|---------|------|
| Logo container | 88x88px, radius: 24px, bg: rgba(255,255,255,0.15), backdrop-filter: blur(8px) |
| Logo text | 32px ExtraBold 800 white, letter-spacing: -1px |
| App name | 28px Bold 700 white |
| Tagline | 14px Regular white/50% opacity |
| Spinner | 36px, 3px border, top: white/60%, rest: white/15%, 0.8s rotation |
| Auto-advance | 2 seconds or tap to continue |

### Screen 2: Onboarding (3 screens)

**Layout:** Full screen, centered content, gradient background
**Background:** linear-gradient(135deg, #4A90D9, #3A7BC8)

| Element | Spec |
|---------|------|
| Icon container | 100x100px, radius: 28px, bg: rgba(255,255,255,0.15) |
| Icon | 48px emoji, centered |
| Title | 24px Bold white, center align, line-height: 1.3 |
| Description | 15px Regular, white/65% opacity, center align, line-height: 1.6, max-width: 300px |
| Page dots | 8px circles, inactive: white/30%, active: white (24px wide, 4px radius) |
| Gap between dots | 8px |
| Next button | max-width: 280px, bg: rgba(255,255,255,0.15), backdrop-filter: blur(8px), radius: 12px |
| Get Started button | max-width: 280px, bg: white, color: #4A90D9, radius: 12px |

**Onboarding Content:**
1. Upload & Organize — Upload prescriptions, lab reports, scan paper documents
2. AI Health Story — Records become a structured health summary in plain language
3. Share Securely — Show a QR code to let your doctor scan and see your history

### Screen 3: Signup

**Layout:** Status bar + scrollable form content

| Element | Spec |
|---------|------|
| Title | 26px Bold #1A1A2E |
| Subtitle | 14px Regular #6B7280, line-height: 1.5, 28px bottom margin |
| Input label | 13px Semibold #6B7280, 8px bottom margin |
| Text input | Full width, 16px padding, bg: white, border: 2px #E5E7EB, radius: 12px, font: 16px Medium |
| Text input focused | border: 2px #4A90D9 |
| Phone input | Same as text input, with +91 prefix (16px Regular #6B7280) |
| Phone number font | 18px Semibold #1A1A2E |
| Input spacing | 20px between fields |
| Submit button | Full width, 52px, bg: #4A90D9, radius: 12px, font: 16px Semibold white |
| Login link | 13px Regular #6B7280, with #4A90D9 Semibold link |

### Screen 4: Login

**Layout:** Status bar + centered content

| Element | Spec |
|---------|------|
| Logo | 64x64px, radius: 18px, bg: #4A90D9 |
| Title | 26px Bold #1A1A2E |
| Subtitle | 14px Regular #6B7280 |
| Phone input | Same spec as signup |
| Submit button | Full width, 52px, bg: #4A90D9, radius: 12px |
| Signup link | 13px Regular #6B7280, with #4A90D9 Semibold link |

### Screen 5: OTP Verification

**Layout:** Status bar + centered content with numpad

| Element | Spec |
|---------|------|
| Title | 26px Bold #1A1A2E |
| Subtitle | 14px Regular #6B7280 |
| Phone display | 16px Semibold #1A1A2E |
| OTP boxes | 6 boxes, each 48x56px, radius: 12px, border: 2px #E5E7EB, bg: white |
| OTP box filled | border: 2px #4A90D9, bg: #E8F1FB |
| OTP digit font | 24px Bold monospace #1A1A2E |
| Gap between boxes | 12px |
| Numpad grid | 3 columns, 12px gap, max-width: 300px |
| Numpad key | full-width x 52px, radius: 12px, bg: white, shadow-sm |
| Numpad font | 22px Semibold #1A1A2E |
| Backspace key | bg: #F8FAFC, no shadow |
| Resend link | 13px Regular #6B7280, with #4A90D9 Semibold timer |

### Screen 6: Home Dashboard

**Layout:** Header + scrollable content + bottom nav
**Status bar:** 14px Semibold, 14px top / 8px bottom padding, 28px horizontal

| Element | Spec |
|---------|------|
| Greeting | 28px Bold, color: #1A1A2E |
| Subtitle | 14px Regular, color: #6B7280, 4px top margin |
| Avatar | 44x44px circle, bg: #4A90D9, text: 18px Bold white |
| Summary card | Gradient: #4A90D9 → #3A7BC8 (135deg), radius: 16px, padding: 24px, shadow-primary |
| Condition tags | bg: rgba(255,255,255,0.2), padding: 6px 14px, radius: 20px, font: 13px Medium |
| Pending alert | bg: #FEF3C7, radius: 8px, border-left: 4px solid #F39C12, padding: 14px 16px |
| Quick actions grid | 2 columns, 12px gap |
| Quick action card | bg: white, border: 1px #E5E7EB, radius: 12px, padding: 20px 12px, center content |
| Quick action icon | 48x48px, radius: 14px |
| Section header | 18px Bold + 14px Semibold #4A90D9 link, 16px bottom margin |
| Medication card | bg: white, radius: 16px, padding: 20px, border: 1px #E5E7EB |
| Med item | 14px gap, 14px vertical padding, bottom border 1px #E5E7EB |
| Med icon | 44x44px, radius: 12px, bg: #E8F1FB |
| Med name | 15px Semibold #1A1A2E |
| Med dose | 13px Regular #6B7280 |
| Lab row | between layout, 14px vertical padding, border bottom |
| Lab value normal | 14px Semibold #27AE60 |
| Lab value high | 14px Semibold #E74C3C |
| Lab status badge | 11px Semibold, padding: 3px 10px, radius: 20px |

**Bottom Navigation:**
- Height: 12px top + item + 28px bottom (safe area)
- Items: 4 (Home, Timeline, Upload, Share)
- Icon: 24x24px, inactive #9CA3AF, active #4A90D9
- Label: 11px, inactive #9CA3AF Medium, active #4A90D9 Semibold

### Screen 7: Share (QR Code)

**Layout:** Centered content + bottom nav

| Element | Spec |
|---------|------|
| Lock icon container | 72x72px, radius: 24px, bg: #E8F1FB |
| Title | 20px Bold #1A1A2E |
| Subtitle | 14px Regular #6B7280, max-width: 280px, center, line-height: 1.5 |
| Consent card | bg: #E8F1FB, radius: 16px, padding: 20px, border: 1px rgba(74,144,217,0.2), full width |
| Consent title | 16px Bold #1A1A2E, 12px bottom margin |
| Consent item icon | 16px emoji, flex-shrink: 0 |
| Consent item text | 14px Regular #6B7280, with bold #1A1A2E for emphasis, line-height: 1.5 |
| QR code container | 200x200px, bg: white, padding: 16px, border: 2px #4A90D9, radius: 16px |
| QR code | 168x168px centered within container, high contrast black-on-white |
| Timer ring | 32px circle, 3px border, top: #4A90D9, rest: #E5E7EB |
| Timer number | 11px Bold #4A90D9 inside ring |
| Timer text | 14px Regular #6B7280 |
| Security badge | bg: #F0FDF4, radius: 12px, padding: 12px 20px |
| Access history button | bg: white, border: 1px, radius: 12px, padding: 14px, full width |

**QR Code Details:**
- Encodes an encrypted session token (not health data)
- Auto-refreshes every 60 seconds with fade-out/fade-in animation
- High error correction level (L25) for reliable scanning in various lighting
- Minimum quiet zone of 4 modules around QR code

### Screen 8: Upload Records

**Layout:** Back header + scrollable content + bottom nav

| Element | Spec |
|---------|------|
| Upload area | border: 2px dashed #4A90D9, radius: 16px, padding: 48px 24px, bg: #E8F1FB |
| Camera icon | 48px emoji |
| Upload text | 16px Semibold #3A7BC8 |
| Upload subtext | 13px Regular #6B7280 |
| Option buttons | 3 columns, 12px gap, bg: white, border: 1px, radius: 12px, padding: 20px 12px |
| Document cards | bg: white, radius: 12px, border: 1px, padding: 16px, 12px margin-bottom |
| Doc icon | 48x48px, radius: 12px |
| Doc title | 15px Semibold |
| Doc meta | 12px Regular #6B7280 |

### Screen 9: Timeline

**Layout:** Header + scrollable content + bottom nav

| Element | Spec |
|---------|------|
| Timeline connector | 2px vertical line, color: #E5E7EB, left: 19px from item start |
| Timeline dot | 40x40px, radius: 12px, color-coded by type |
| Date | 12px Medium #9CA3AF |
| Event title | 15px Semibold #1A1A2E |
| Event detail | 13px Regular #6B7280, line-height: 1.5 |

### Screen 10: Access Log

**Layout:** Back header + scrollable content

| Element | Spec |
|---------|------|
| Description | 14px Regular #6B7280, line-height: 1.5, 24px bottom margin |
| Access avatar | 44px circle, bg: #E8F1FB |
| Access name | 15px Semibold #1A1A2E |
| Access meta | 12px Regular #6B7280, 2px top margin |
| Duration badge | 12px Medium #9CA3AF, bg: #F8FAFC, padding: 4px 10px, radius: 8px |

### Screen 11: Profile & Settings

**Layout:** Status bar + scrollable content + bottom nav

| Element | Spec |
|---------|------|
| Avatar | 88x88px circle, gradient bg: #4A90D9 → #3A7BC8, centered |
| Avatar initials | 32px Bold white |
| Name | 22px Bold #1A1A2E, center |
| Subtitle | 14px Regular #6B7280, center |
| Settings section label | 12px Bold #9CA3AF, uppercase, 0.8px spacing |
| Settings item | bg: white, radius: 12px, border: 1px #E5E7EB, padding: 16px, 10px margin-bottom |
| Settings icon | 40x40px, radius: 10px, color-coded bg |
| Settings title | 15px Semibold #1A1A2E |
| Settings description | 12px Regular #6B7280 |
| Chevron | 18px #9CA3AF |
| Logout button | Full width, border: 2px #E74C3C, color: #E74C3C, radius: 12px |

---

## Doctor App — Screen Specifications

### Screen 1: Splash

**Layout:** Full screen centered, gradient background
**Background:** linear-gradient(135deg, #2C3E50, #1A252F)

| Element | Spec |
|---------|------|
| Logo container | 88x88px, radius: 24px, bg: rgba(255,255,255,0.1), backdrop-filter: blur(8px) |
| Logo text | 32px ExtraBold 800 white, letter-spacing: -1px |
| App name | 28px Bold 700 white |
| Subtitle | 15px Regular white/60% opacity ("For Doctors") |
| Tagline | 13px Regular white/40% opacity |
| Spinner | 36px, 3px border, top: white/60%, rest: white/15%, 0.8s rotation |
| Auto-advance | 2 seconds or tap to continue |

### Screen 2: Onboarding (3 screens)

**Layout:** Full screen, centered content, gradient background
**Background:** linear-gradient(135deg, #2C3E50, #34495E)

| Element | Spec |
|---------|------|
| Icon container | 100x100px, radius: 28px, bg: rgba(255,255,255,0.1) |
| Icon | 48px emoji, centered |
| Title | 24px Bold white, center align, line-height: 1.3 |
| Description | 15px Regular, white/65% opacity, center align, line-height: 1.6, max-width: 300px |
| Page dots | 8px circles, inactive: white/30%, active: white (24px wide, 4px radius) |
| Gap between dots | 8px |
| Next button | max-width: 280px, bg: rgba(255,255,255,0.15), backdrop-filter: blur(8px), radius: 12px |
| Get Started button | max-width: 280px, bg: white, color: #2C3E50, radius: 12px |

**Onboarding Content:**
1. 30-Second Patient Briefing — Structured summary: conditions, medications, lab results, alerts
2. Patient-Consented Access — Scan patient's QR code, auto-expiring sessions, no data stored on device
3. Works With Existing Setup — Complements HMS, no queues, no booking, no admin

### Screen 3: Hospital Invite Code

**Layout:** Status bar + centered content

| Element | Spec |
|---------|------|
| Hospital icon container | 72x72px, radius: 20px, bg: #E8F1FB |
| Icon | 32px emoji |
| Title | 24px Bold #1A1A2E |
| Description | 14px Regular #6B7280, line-height: 1.6, max-width: 300px |
| Help link | 13px Medium #4A90D9, clickable |
| Invite boxes | 8 boxes, each 44x56px, radius: 12px, border: 2px #E5E7EB, bg: white |
| Invite box filled | border: 2px #4A90D9, bg: #E8F1FB |
| Invite font | 22px Bold monospace #1A1A2E, text-transform: uppercase |
| Gap between boxes | 10px |
| Info banner | bg: #E8F1FB, radius: 8px, padding: 14px 18px, full width |
| Info icon | 18px emoji |
| Info text | 13px Regular #3A7BC8, line-height: 1.4 |
| Verify button | Full width, 52px, bg: #4A90D9, radius: 12px, font: 16px Semibold white |
| Login link | 13px Regular #6B7280, with #4A90D9 Semibold link |

### Screen 4: Signup (Doctor Registration)

**Layout:** Status bar + scrollable form content

| Element | Spec |
|---------|------|
| Back button | 14px Semibold #4A90D9, with ← arrow, 16px bottom margin |
| Title | 26px Bold #1A1A2E |
| Subtitle | 14px Regular #6B7280, line-height: 1.5, 28px bottom margin |
| Input label | 13px Semibold #6B7280, 8px bottom margin |
| Text input | Full width, 16px padding, bg: white, border: 2px #E5E7EB, radius: 12px, font: 16px Medium |
| Text input focused | border: 2px #4A90D9 |
| Phone input | Same as text input, with +91 prefix (16px Regular #6B7280) |
| Phone number font | 18px Semibold #1A1A2E |
| Input spacing | 20px between fields |
| Hospital verified banner | bg: #F0FDF4, radius: 8px, border-left: 4px #27AE60, padding: 14px 16px |
| Verified icon | 16px checkmark emoji |
| Verified title | 13px Semibold #166534 |
| Verified subtitle | 12px Regular #15803D |
| Submit button | Full width, 52px, bg: #4A90D9, radius: 12px, font: 16px Semibold white |

**Form Fields:**
1. Full Name (prefilled with "Dr. " prefix)
2. Specialization (text input)
3. Registration Number (MCI / State council number)
4. Phone Number (with +91 prefix)

### Screen 5: Login (Doctor)

**Layout:** Status bar + centered content

| Element | Spec |
|---------|------|
| Logo | 64x64px, radius: 18px, bg: #2C3E50 |
| Logo text | 22px ExtraBold white, letter-spacing: -1px |
| Title | 26px Bold #1A1A2E ("Welcome Back") |
| Subtitle | 14px Regular #6B7280 |
| Phone input | Same spec as signup |
| Submit button | Full width, 52px, bg: #4A90D9, radius: 12px |
| Register link | 13px Regular #6B7280, with #4A90D9 Semibold link |

### Screen 6: OTP Verification (Doctor)

**Layout:** Status bar + centered content with numpad

| Element | Spec |
|---------|------|
| Title | 26px Bold #1A1A2E |
| Subtitle | 14px Regular #6B7280 |
| Phone display | 16px Semibold #1A1A2E |
| OTP boxes | 6 boxes, each 48x56px, radius: 12px, border: 2px #E5E7EB, bg: white |
| OTP box filled | border: 2px #4A90D9, bg: #E8F1FB |
| OTP digit font | 24px Bold monospace #1A1A2E |
| Gap between boxes | 12px |
| Numpad | Same spec as patient app numpad |
| Resend link | 13px Regular #6B7280, with #4A90D9 Semibold timer |

### Screen 7: Dashboard

**Layout:** Header + scrollable content + bottom nav

| Element | Spec |
|---------|------|
| Greeting line | 14px Regular #6B7280 ("Good morning,") |
| Doctor name | 24px Bold #1A1A2E |
| Profile avatar | 44x44px circle, gradient: #2C3E50 → #4A90D9, initials: 16px Bold white |
| Scan QR Code CTA | Full width, gradient: #2C3E50 → #1A2530 (135deg), radius: 16px, padding: 28px 24px, shadow-doctor |
| CTA icon | 36px, centered, 12px bottom margin |
| CTA title | 20px Bold white |
| CTA subtitle | 14px Regular white/75% opacity |
| Today's Patients header | 18px Bold #1A1A2E + 13px Medium #9CA3AF count |
| Patient item | bg: white card, 14px vertical padding, border-bottom: 1px #E5E7EB |
| Patient avatar | 44px circle, colored bg (varies), initials: 16px Bold white |
| Patient name | 15px Semibold #1A1A2E |
| Patient meta | 12px Regular #6B7280 (age + key conditions) |
| Time viewed | 12px Medium #9CA3AF |
| Stats row | 2 columns, 12px gap |
| Stat box | bg: white, radius: 12px, border: 1px #E5E7EB, padding: 16px, center |
| Stat value | 28px Bold #1A1A2E (or #4A90D9 for time) |
| Stat label | 12px Regular #6B7280, 4px top margin |

**Bottom Navigation:**
- Height: 12px top + item + 28px bottom (safe area)
- Items: 3 (Home, Scan QR, Profile)
- Icon: 24x24px, inactive #9CA3AF, active #4A90D9
- Label: 11px, inactive #9CA3AF Medium, active #4A90D9 Semibold

### Screen 8: QR Scanner

**Layout:** Full screen with camera viewfinder, bottom nav
**Background:** #000000 (camera feed)

| Element | Spec |
|---------|------|
| Logo | 64x64px, radius: 16px, bg: #2C3E50, text: 18px ExtraBold white |
| Title | 24px Bold white (overlaid on camera) |
| Subtitle | 14px Regular white/75%, line-height: 1.5 |
| Scanner frame | 250x250px, centered, border: 3px white, corner accents: 40px #4A90D9 |
| Scanner overlay | Semi-transparent black (#000 at 60% opacity) outside scanner frame |
| Scanning animation | Horizontal line sweep inside frame, color: #4A90D9, 2s loop |
| Instruction text | 14px Semibold white, below scanner frame, 16px margin-top |
| Feedback: scanning | bg: #E8F1FB, color: #4A90D9, radius: 8px, padding: 12px 20px |
| Feedback: error | bg: #FEF2F2, color: #E74C3C |
| Auto-detect | Camera detects QR code automatically → vibrate → feedback → navigate |
| Flashlight toggle | 44x44px circle, bg: rgba(255,255,255,0.2), bottom-right of frame |

### Screen 9: Loading

**Layout:** Full screen centered

| Element | Spec |
|---------|------|
| Spinner | 56px, 4px border, top: #4A90D9, rest: #E5E7EB, 0.8s rotation |
| Title | 16px Semibold #1A1A2E |
| Subtitle | 14px Regular #6B7280, 6px top margin |

### Screen 10: Briefing Card

**Layout:** Fixed header + critical banner + scrollable sections + fixed done bar

**Patient Header (fixed):**
| Element | Spec |
|---------|------|
| Container | bg: white, border-bottom: 1px #E5E7EB, padding: 20px 24px |
| Patient name | 22px Bold #1A1A2E |
| Patient meta | 14px Regular #6B7280, 4px top margin |
| Session badge | bg: #F0FDF4, color: #27AE60, padding: 6px 14px, radius: 20px, 12px Semibold |

**Critical Alert Banner (fixed):**
| Element | Spec |
|---------|------|
| Container | bg: #E74C3C, padding: 14px 24px, full width |
| Icon | 22px, flex-shrink: 0 |
| Title | 14px Semibold white |
| Subtitle | 12px Regular white/85% opacity |
| Note | Only shown when critical alerts exist. Multiple alerts stack. |

**Section Pattern (repeating):**
| Element | Spec |
|---------|------|
| Container | bg: white, border-bottom: 1px #E5E7EB |
| Section head | padding: 16px 24px |
| Section label | 12px Bold #9CA3AF, uppercase, 0.8px letter-spacing |
| Count badge | 12px Semibold, bg: #E8F1FB, color: #4A90D9, padding: 2px 10px, radius: 10px |
| Section body | padding: 0 24px 16px |

**Condition Chips:**
| Element | Spec |
|---------|------|
| Active chip | bg: #EBF5FF, color: #1E40AF, padding: 8px 16px, radius: 24px, 14px Semibold |
| Monitoring chip | bg: #FEF3C7, color: #92400E |
| Gap | 8px |

**Medication Rows:**
| Element | Spec |
|---------|------|
| Row padding | 10px vertical, border-bottom: 1px #F3F4F6 |
| Med dot | 8px circle, bg: #4A90D9 |
| Drug name | 15px Semibold #1A1A2E |
| Drug detail | 13px Regular #6B7280, 2px top margin |
| Frequency badge | 12px Semibold #9CA3AF, bg: #F8FAFC, padding: 4px 10px, radius: 8px |

**Lab Values:**
| Element | Spec |
|---------|------|
| Name | 14px Medium #1A1A2E |
| Range | 11px Regular #9CA3AF |
| Value normal | 14px Bold #27AE60 |
| Value high | 14px Bold #E74C3C + "↑" |
| Value low | 14px Bold #F39C12 + "↓" |

**Last Visit Card:**
| Element | Spec |
|---------|------|
| Container | bg: #F8FAFC, radius: 12px, border-left: 4px #4A90D9, padding: 16px |
| Doctor name | 15px Semibold #1A1A2E |
| Hospital | 13px Regular #6B7280 |
| Date | 12px Regular #9CA3AF, 8px top margin |
| Note | 14px Regular #6B7280, line-height: 1.5, 10px top margin |

**Pending Investigations:**
| Element | Spec |
|---------|------|
| Item container | bg: #FEF3C7, radius: 8px, border-left: 4px #F39C12, padding: 12px 16px |
| Item gap | 10px between items |
| Icon | 18px emoji |
| Title | 14px Medium #92400E |
| Age text | 12px Regular #B45309, 4px top margin |

**Done Bar (fixed bottom):**
| Element | Spec |
|---------|------|
| Container | bg: white, border-top: 1px #E5E7EB, padding: 16px 24px 32px |
| Button | Full width, 52px, bg: #2C3E50, radius: 12px, text: 16px Semibold white |

### Screen 11: Session Ended

**Layout:** Full screen centered

| Element | Spec |
|---------|------|
| Icon circle | 80px, bg: #F0FDF4, checkmark: 40px |
| Title | 22px Bold #1A1A2E |
| Description | 14px Regular #6B7280, line-height: 1.5, center |
| Back to Dashboard button | max-width: 300px, bg: #4A90D9, radius: 12px |
| Scan Next Patient button | max-width: 300px, bg: transparent, border: 2px #E5E7EB, color: #2C3E50, radius: 12px |
| Button spacing | 12px between buttons |

### Screen 12: Profile & Settings

**Layout:** Status bar + scrollable content + bottom nav

| Element | Spec |
|---------|------|
| Avatar | 88x88px circle, gradient: #2C3E50 → #4A90D9, centered |
| Avatar initials | 32px Bold white |
| Name | 22px Bold #1A1A2E, center |
| Specialization | 14px Regular #6B7280, center, 4px top margin |
| Hospital | 13px Medium #4A90D9, center, 4px top margin |
| Stats row | 2 columns, 12px gap, 24px vertical margin |
| Stat box | bg: white, radius: 12px, border: 1px #E5E7EB, padding: 16px, center |
| Stat value | 24px Bold #1A1A2E (or #4A90D9 for time) |
| Stat label | 12px Regular #6B7280, 4px top margin |
| Section label | 12px Bold #9CA3AF, uppercase, 0.8px spacing, 12px bottom margin |
| Settings item | bg: white, radius: 12px, border: 1px #E5E7EB, padding: 16px, 10px margin-bottom |
| Settings icon | 40x40px, radius: 10px, color-coded bg, 18px emoji centered |
| Settings title | 15px Semibold #1A1A2E |
| Settings description | 12px Regular #6B7280, 2px top margin |
| Chevron | 18px #9CA3AF |
| Logout button | Full width, border: 2px #E74C3C, color: #E74C3C, bg: transparent, radius: 12px |
| Version text | 12px Regular #9CA3AF, center, 20px top margin |

**Settings Sections:**
- Account: Edit Profile, Hospital, Phone Number
- Preferences: Notifications, Language
- Support: Help & FAQ, Privacy & Data (DPDPA)

---

## Interaction Specifications

### Authentication Flow (Both Apps)

**OTP Entry:**
- 6-digit OTP, auto-focus first box on load
- Each digit typed fills next box with subtle scale animation
- On 6th digit: auto-verify → navigate to home/dashboard
- Invalid OTP: shake animation, red border flash, clear boxes
- Resend timer: starts at 30s, countdown, re-enables "Resend" at 0

**Hospital Invite Code (Doctor only):**
- 8-character alphanumeric, uppercase display
- Auto-validates on 8th character
- Invalid code: shake animation, error message below
- Successfully verified: green success banner with hospital name

### Patient App: QR Code Rotation
- QR code refreshes every **60 seconds** with a new encrypted token
- Circular timer animation: 60s linear rotation on timer ring border
- When QR refreshes: fade-out old QR code (150ms), fade-in new QR code (200ms)
- QR encodes: encrypted session token (server-generated, time-limited)
- QR error correction: Level L (25%) for reliable scanning

### Doctor App: QR Scanner
- Camera activates automatically on screen load (requires camera permission)
- Scanner frame centered on screen with corner accent markers
- Horizontal scanning line animation sweeps through frame
- On successful scan: haptic vibrate → "Finding patient..." feedback → 800ms → loading screen → 1400ms → briefing
- Invalid QR: shake animation on scanner frame, red border flash, error message below
- Flashlight toggle for low-light environments

### Briefing Card: Progressive Disclosure
- All sections start expanded (doctor needs to see everything at a glance)
- Tapping section header collapses/expands with 200ms slide animation
- Critical alert banner is ALWAYS visible (never collapses, not scrollable away)

### Session Management
- Access auto-expires after **20 minutes**
- "Done" button immediately revokes access and shows confirmation
- Patient receives push notification: "Dr. [Name] viewed your summary"
- Session ended screen offers two paths: back to dashboard or enter next code

### Dashboard: Today's Patients
- List auto-clears daily at midnight
- Shows patients viewed today with time, name, key conditions
- Tapping a patient does NOT re-open their data (requires new code for privacy)
- Useful for referring back to who was seen earlier

---

## Navigation Architecture

### Patient App

```
Splash → Onboarding (3 screens) → Signup / Login → OTP → Home
                                                          ├── Home (dashboard)
                                                          ├── Timeline
                                                          ├── Upload → Camera → OCR → Review
                                                          ├── Share (QR code + consent)
                                                          │   └── Access Log
                                                          └── Profile & Settings
```

### Doctor App

```
Splash → Onboarding (3 screens) → Invite Code → Signup → OTP → Dashboard
                                  Login → OTP → Dashboard
                                                    ├── Dashboard (Home)
                                                    │   └── Today's Patients list
                                                    ├── Scan QR Code → Loading → Briefing Card
                                                    │   └── Done → Session Ended
                                                    └── Profile & Settings
```

---

## Accessibility Checklist

- [ ] All text meets WCAG 2.1 AA contrast ratio (4.5:1 normal, 3:1 large)
- [ ] All touch targets ≥ 48x48px
- [ ] Color never used alone to convey meaning (always paired with icon/text)
- [ ] Full VoiceOver (iOS) and TalkBack (Android) support
- [ ] Labels on all interactive elements for screen readers
- [ ] Font scalable to 200% without breaking layout
- [ ] No information hidden behind hover states
- [ ] Critical alerts have distinct shape + color + icon + text
- [ ] QR code has sufficient quiet zone and error correction for reliable scanning
- [ ] Invite code input supports uppercase auto-conversion
- [ ] Camera permission requested clearly with explanation for QR scanning
- [ ] Bottom nav respects safe area insets on both platforms
