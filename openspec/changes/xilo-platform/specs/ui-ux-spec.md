# Spec: UI/UX Design System

## Overview

Complete UI/UX design system for Xilo web and mobile platforms. Inspired by X/Twitter (structure, profiles, actions) and Telegram (chat bubbles, simplicity, smooth animations). Minimalist, content-first, with generous whitespace and modern typography.

This file is the normative visual authority for Xilo. Other proposals, designs, and domain specifications SHALL reference these tokens and patterns rather than define conflicting visual values.

---

## 1. Design Principles

1. **Content-first**: UI recedes; content leads
2. **Consistent patterns**: Same interaction, same behavior everywhere
3. **Progressive disclosure**: Show what's needed, hide the rest
4. **Meaningful motion**: Animations guide, never distract
5. **Accessible by default**: WCAG 2.1 AA minimum
6. **Mobile-first**: Design for small, enhance for large

### 1.1 Icon-and-Title Heading Pattern

Whenever an icon accompanies a heading or title, the icon and title SHALL appear on the same horizontal row and SHALL never be stacked with the icon above the title. The row SHALL center-align its items with an 8px gap (12px for a larger icon container); the icon SHALL not shrink, and the title SHALL be allowed to shrink/wrap safely for long Persian text. Supporting description text SHALL remain below the icon-title row.

---

## 2. Color System

### 2.1 Light Mode Palette

```typescript
const light = {
  // Primary
  primary: '#1D9BF0',
  primaryHover: '#1A8CD8',
  primaryPressed: '#1A7BC5',
  primarySurface: '#E8F5FE',

  // Background
  background: '#FFFFFF',
  backgroundSecondary: '#F7F9FA',
  backgroundTertiary: '#EFF3F4',

  // Text
  textPrimary: '#0F1419',
  textSecondary: '#536471',
  textTertiary: '#8295A3',
  textLink: '#1D9BF0',

  // Border
  border: '#EFF3F4',
  borderStrong: '#CFD9DE',

  // Semantic
  error: '#F4212E',
  errorSurface: '#FDE8E8',
  success: '#00BA7C',
  successSurface: '#E0F5EC',
  warning: '#FFAD1F',
  warningSurface: '#FFF4E0',
  info: '#1D9BF0',
  infoSurface: '#E8F5FE',

  // Comment Bubbles
  bubbleOwn: '#E8F5FE',
  bubbleOthers: '#F7F9FA',
  bubbleHighlighted: '#FFF9C4',
  bubbleBorder: '#EFF3F4',

  // Chat Bubbles
  chatBubbleOwn: '#E8F5FE',
  chatBubbleOthers: '#F0F2F5',

  // Overlay
  overlay: 'rgba(0, 0, 0, 0.4)',
  backdropBlur: 'rgba(255, 255, 255, 0.8)',
};
```

### 2.2 Dark Mode Palette

```typescript
const dark = {
  // Primary
  primary: '#1D9BF0',
  primaryHover: '#4DB8F5',
  primaryPressed: '#6BC9F7',
  primarySurface: '#1A2A3A',

  // Background
  background: '#15202B',
  backgroundSecondary: '#192734',
  backgroundTertiary: '#22303C',

  // Text
  textPrimary: '#E7E9EA',
  textSecondary: '#71767B',
  textTertiary: '#536471',
  textLink: '#6BC9F7',

  // Border
  border: '#38444D',
  borderStrong: '#4A5A66',

  // Semantic
  error: '#F4212E',
  errorSurface: '#3D1A1E',
  success: '#00BA7C',
  successSurface: '#1A3D2E',
  warning: '#FFAD1F',
  warningSurface: '#3D2E1A',
  info: '#1D9BF0',
  infoSurface: '#1A2A3A',

  // Comment Bubbles
  bubbleOwn: '#1E3A5F',
  bubbleOthers: '#2C2C2E',
  bubbleHighlighted: '#3E3A2F',
  bubbleBorder: '#38444D',

  // Chat Bubbles
  chatBubbleOwn: '#1E3A5F',
  chatBubbleOthers: '#2C2C2E',

  // Overlay
  overlay: 'rgba(0, 0, 0, 0.6)',
  backdropBlur: 'rgba(21, 32, 43, 0.8)',
};
```

### 2.3 Gradients

```typescript
const gradients = {
  hero: 'linear-gradient(135deg, #1D9BF0 0%, #7C3AED 100%)',
  cardHover: 'linear-gradient(180deg, transparent 0%, rgba(29,155,240,0.05) 100%)',
  skeleton: 'linear-gradient(90deg, #F0F0F0 25%, #E0E0E0 50%, #F0F0F0 75%)',
  avatarPlaceholder: 'linear-gradient(135deg, #E0E0E0 0%, #F5F5F5 100%)',
};
```

---

## 3. Typography

### 3.1 Font Families

| Purpose | Font | Fallback |
|---------|------|----------|
| English (LTR) | Inter | -apple-system, BlinkMacSystemFont, Segoe UI |
| Persian (RTL) | Vazirmatn | Tahoma, Arial |
| Arabic (RTL) | Noto Sans Arabic | Tahoma, Arial |
| Code | JetBrains Mono | Fira Code, monospace |

### 3.2 Type Scale

| Name | Size | Line Height | Weight | Usage |
|------|------|-------------|--------|-------|
| Display | 32px | 1.2 | 700 | Hero titles |
| H1 | 24px | 1.3 | 700 | Page titles |
| H2 | 20px | 1.3 | 700 | Section titles |
| H3 | 18px | 1.4 | 600 | Card titles |
| Body Large | 17px | 1.5 | 400 | Lead paragraphs |
| Body | 15px | 1.5 | 400 | Default text |
| Body Small | 13px | 1.4 | 400 | Captions, metadata |
| Caption | 11px | 1.3 | 400 | Timestamps, labels |
| Button | 15px | 1.2 | 600 | Button text |
| Button Small | 13px | 1.2 | 600 | Small buttons |

### 3.3 Font Loading Strategy

**Web (Next.js):**
```typescript
const inter = Inter({
  subsets: ['latin'],
  variable: '--font-inter',
  display: 'swap',
})

const vazirmatn = Vazirmatn({
  subsets: ['arabic'],
  variable: '--font-vazirmatn',
  display: 'swap',
  weight: ['100', '200', '300', '400', '500', '600', '700', '800', '900'],
})
```

**Mobile (Native Android):**
The active Android app SHALL package Inter and Vazirmatn through Android resources and apply them through its Compose typography. Font selection, locale behavior, and RTL requirements are governed by `openspec/changes/android-native-production/specs/android-i18n/spec.md`. The Flutter mobile tree is legacy and does not define active UI implementation.

---

## 4. Spacing System

### 4.1 Base Scale (4px grid)

| Token | Value | Usage |
|-------|-------|-------|
| `space-1` | 4px | Tight gaps (icon + text) |
| `space-2` | 8px | Small gaps (inline elements) |
| `space-3` | 12px | Medium gaps (form fields) |
| `space-4` | 16px | Default gap (card padding) |
| `space-5` | 20px | Section gaps |
| `space-6` | 24px | Large gaps (page sections) |
| `space-8` | 32px | XL gaps (page padding) |
| `space-10` | 40px | Hero spacing |
| `space-12` | 48px | Page margins |
| `space-16` | 64px | Hero sections |

### 4.2 Layout Spacing

| Context | Spacing |
|---------|---------|
| Page padding (mobile) | 16px horizontal |
| Page padding (desktop) | 24px horizontal, max-width 1200px |
| Card padding | 16px |
| Comment bubble padding | 12-14px |
| Chat bubble padding | 8-12px |
| Navbar height | 56px |
| Sidebar width (desktop) | 280px |
| Mobile bottom nav height | 64px |

---

## 5. Border Radius

| Token | Value | Usage |
|-------|-------|-------|
| `none` | 0 | Images (full bleed) |
| `sm` | 4px | Buttons, inputs, tags |
| `md` | 8px | Cards, modals, dropdowns |
| `lg` | 12px | Media containers |
| `xl` | 16px | Comment/chat bubbles, images |
| `full` | 9999px | Avatars, pills, badges |

---

## 6. Shadows

| Token | Value | Usage |
|-------|-------|-------|
| `xs` | `0 1px 2px rgba(0,0,0,0.05)` | Subtle elevation |
| `sm` | `0 1px 3px rgba(0,0,0,0.1), 0 1px 2px rgba(0,0,0,0.06)` | Cards, dropdowns |
| `md` | `0 4px 6px rgba(0,0,0,0.1), 0 2px 4px rgba(0,0,0,0.06)` | Modals, popovers |
| `lg` | `0 10px 15px rgba(0,0,0,0.1), 0 4px 6px rgba(0,0,0,0.05)` | Floating elements |
| `xl` | `0 20px 25px rgba(0,0,0,0.1), 0 10px 10px rgba(0,0,0,0.04)` | Hero overlays |
| `inner` | `inset 0 2px 4px rgba(0,0,0,0.06)` | Input focus, pressed |

---

## 7. Responsive Breakpoints

| Name | Min Width | Target |
|------|-----------|--------|
| `xs` | 0px | Small phones |
| `sm` | 640px | Large phones |
| `md` | 768px | Tablets |
| `lg` | 1024px | Small laptops |
| `xl` | 1280px | Desktops |
| `2xl` | 1536px | Large screens |

### 7.1 Layout Behavior by Breakpoint

| Element | xs-sm | md | lg+ |
|---------|-------|----|-----|
| Navbar | Compact (icon only) | Full | Full |
| Sidebar | Hidden (drawer) | Hidden (drawer) | Visible |
| Feed | Single column | Single column | 2-col (feed + sidebar) |
| Post detail | Full width | Max 680px | Max 680px centered |
| Comment bubbles | Full width | Max 600px | Max 600px |
| Chat bubbles | Full width | Max 600px | Max 600px |
| Bottom nav | Visible (6 items) | Visible (6 items) | Hidden |

---

## 8. Component Specifications

### 8.1 Buttons

```
┌─────────────────────┐
│  [Icon] Button Text  │  ← Primary
└─────────────────────┘

┌─────────────────────┐
│  [Icon] Button Text  │  ← Secondary (border)
└─────────────────────┘

     Button Text        ← Ghost (no background)
```

| Property | Primary | Secondary | Ghost | Danger |
|----------|---------|-----------|-------|--------|
| Background | `primary` | `transparent` | `transparent` | `error` |
| Text | `white` | `textPrimary` | `textSecondary` | `white` |
| Border | `none` | `borderStrong` | `none` | `none` |
| Hover | `primaryHover` | `backgroundSecondary` | `backgroundSecondary` | `errorHover` |
| Height | 40px | 40px | 36px | 40px |
| Radius | `sm` (4px) | `sm` (4px) | `sm` (4px) | `sm` (4px) |
| Padding | 0 16px | 0 16px | 0 12px | 0 16px |

**Icon Button:**
- Size: 36×36px (default), 32×32px (small)
- Radius: `full` (circle)
- Hover: `backgroundSecondary`

### 8.2 Input Fields

```
Label
┌─────────────────────┐
│ Placeholder text    │  ← Default
└─────────────────────┘

Label
┌─────────────────────┐
│ Typed text          │  ← Focused (border: primary)
└─────────────────────┘

Label
┌─────────────────────┐
│ Error message       │  ← Error (border: error)
└─────────────────────┘
! This field is required
```

| State | Border | Background | Text |
|-------|--------|------------|------|
| Default | `border` | `background` | `textPrimary` |
| Focused | `primary` (2px) | `background` | `textPrimary` |
| Error | `error` (2px) | `errorSurface` | `textPrimary` |
| Disabled | `border` | `backgroundTertiary` | `textTertiary` |
| Hover | `borderStrong` | `background` | `textPrimary` |

**Height:** 44px (touch-friendly)
**Radius:** `sm` (4px)
**Padding:** 0 12px

### 8.3 Avatar

| Size | Dimensions | Usage |
|------|------------|-------|
| `xs` | 24px | Comment metadata |
| `sm` | 32px | Comment bubbles (mobile) |
| `md` | 40px | Comment bubbles (desktop), chat bubbles |
| `lg` | 48px | Post cards, chat list |
| `xl` | 56px | Profile headers |
| `2xl` | 120px | Profile page hero |

**Online indicator:** 8px green dot, positioned bottom-right with 2px white border
**Verified badge:** 14px blue checkmark, overlapping bottom-right

### 8.4 Cards

**Post Card:**
```
┌─────────────────────────────────────┐
│ 👤  Alex Morgan ✓  @alexmorgan  · 2h  ⋯ │
│                                     │
│ The best ideas come from            │
│ curiosity. Keep questioning,        │
│ keep building. 🚀                   │
│                                     │
│ ┌─────────────────────────────┐     │
│ │     [Image: Mountain]       │     │
│ │                             │     │
│ └─────────────────────────────┘     │
│                                     │
│ 💬 128   🔁 256   ❤️ 1.8K   ↗   📊  │
└─────────────────────────────────────┘
```

| Property | Value |
|----------|-------|
| Padding | 16px |
| Border bottom | 1px `border` |
| Background | `background` |
| Hover | `backgroundSecondary` (subtle) |
| Max width | 680px (centered) |
| Avatar | 48px |
| Image radius | `lg` (12-16px) |
| Action icons | 20px, color `textSecondary` |
| Action hover | `primary` |

**Discover Card:**
```
┌──────────────────────────────────┐
│ 👤 @alice                        │
│ ┌────────────────────────────┐   │
│ │ AI will replace most       │   │
│ │ junior dev jobs in 5 yrs.  │   │
│ └────────────────────────────┘   │
│ ❤️ 120   💬 34                   │
│ ↳ on post: "Future of AI devs"   │
│ by @john                         │
└──────────────────────────────────┘
```

| Property | Value |
|----------|-------|
| Padding | 16px |
| Border | 1px `border` |
| Border radius | `md` (8px) |
| Comment bubble radius | `xl` (16px) |
| Comment bubble bg | `bubbleOthers` |
| Background | `background` |

### 8.5 Comment Bubble (Telegram-Style)

```
┌─────────────────────────────────────┐
│ 👤 Sophia Lee ✓  @sophialee · 1h   │
│                                     │
│ ┌─────────────────────────────┐     │
│ │ Absolutely! Curiosity is    │     │
│ │ the fuel.                   │     │
│ └─────────────────────────────┘     │
│                                     │
│ 🔥 12   💯 3            9:15 AM     │
└─────────────────────────────────────┘
```

| Property | Value |
|----------|-------|
| Bubble radius | `xl` (14-16px) |
| Bubble padding | 12-14px |
| Own bubble bg | `bubbleOwn` (#E8F5FE light, #1E3A5F dark) |
| Others bubble bg | `bubbleOthers` (#F7F9FA light, #2C2C2E dark) |
| Highlighted bg | `bubbleHighlighted` (#FFF9C4 light, #3E3A2F dark) |
| Thread indent | Avatar-column aligned (Twitter-style); no heavy side indent |
| Max visible depth | 2 levels relative to focus root |
| Avatar size | 32px (mobile), 40px (desktop) |
| Reaction row gap | 6-8px below bubble |
| Thread line | 2px `border` / outline, rounded, centered under avatars |

**Thread drill-down:**
- Beyond the 2 visible levels: "**N پاسخ**" / "View N replies" (direct child count)
- Color: `primary`
- Padding: 8px 16px
- Font: 13px, 500 weight
- Click enters that comment as focus root; back returns to previous focus

### 8.6 Chat Bubble (Telegram-Style)

```
                        ┌─────────────────────┐
                        │ Hey, how are you?   │  ← Own (right-aligned)
                        │             10:30 ✓✓│
                        └─────────────────────┘

┌─────────────────────┐
│ I'm good, thanks!   │  ← Others (left-aligned)
│ 10:31               │
└─────────────────────┘
```

| Property | Own | Others |
|----------|-----|--------|
| Alignment | Right | Left |
| Bubble bg | `chatBubbleOwn` | `chatBubbleOthers` |
| Bubble radius | 16px (top-right: 4px) | 16px (top-left: 4px) |
| Max width | 75% | 75% |
| Padding | 8-12px | 8-12px |
| Timestamp | 11px, `textTertiary` | 11px, `textTertiary` |
| Read receipt | 11px, `primary` (read) / `textTertiary` (sent) | N/A |

**Message composer:**
- Height: 44px (min), auto-expand to 120px
- Border: 1px `border`, radius `full`
- Padding: 0 16px
- Send button: `primary` circle, 40px
- Attachment icon: `textSecondary`, 24px
- Emoji picker trigger: `textSecondary`, 24px

### 8.7 Navigation

**Top Navbar (Web):**
```
┌─────────────────────────────────────────────────────┐
│ [Logo] Xilo    [🔍 Search]  [✏️]  [🔔]  [💬]  [👤] │
└─────────────────────────────────────────────────────┘
```

| Property | Value |
|----------|-------|
| Height | 56px |
| Background | `background` |
| Border bottom | 1px `border` |
| Logo | 32px height |
| Search bar | Max 400px, radius `full`, bg `backgroundSecondary` |
| Icon buttons | 40×40px, hover `backgroundSecondary` |
| Notification badge | Red dot, 8px |
| Unread count | Red pill, `error` bg, white text |

**Mobile Bottom Nav:**
```
┌─────────────────────────────────┐
│  🏠    🔍    ✏️    💬    👤     │
│ Home  Disc.  Write Chat Profile │
└─────────────────────────────────┘
```

| Property | Value |
|----------|-------|
| Height | 64px |
| Background | `background` |
| Border top | 1px `border` |
| Icon size | 24px |
| Label | 11px, `textSecondary` (active: `primary`) |
| Active indicator | `primary` icon + label |
| Write button | Floating action button (center, elevated) |

**Sidebar (Desktop):**
```
┌────────────────────┐
│ 📂 Categories      │
│   Tech             │
│   Lifestyle        │
│   Business         │
│                    │
│ 🔥 Trending        │
│   #AI              │
│   #GoLang          │
│                    │
│ 👥 Suggested       │
│   @alice ✓         │
│   @bob             │
└────────────────────┘
```

| Property | Value |
|----------|-------|
| Width | 280px |
| Padding | 16px |
| Background | `background` |
| Border right | 1px `border` |
| Section gap | `space-6` (24px) |
| Item height | 40px |
| Item hover | `backgroundSecondary` |
| Item active | `primarySurface` |

### 8.8 Profile Header

```
┌─────────────────────────────────────┐
│  ← [Logo] Xilo                  ⋯  │
│                                     │
│         ┌─────────────┐             │
│         │   Avatar    │             │
│         │   (120px)   │             │
│         └─────────────┘             │
│                                     │
│      Alex Morgan ✓                  │
│      @alexmorgan                    │
│                                     │
│  Building the future at the         │
│  intersection of technology…      │
│                                     │
│  1,248      256K       1,023        │
│  Posts    Followers   Following     │
│                                     │
│ [Follow] [Message] [Share Profile]  │
│                                     │
│ Posts | Replies | Media | Likes     │
└─────────────────────────────────────┘
```

| Property | Value |
|----------|-------|
| Avatar size | 120px |
| Avatar border | 4px `background`, shadow `md` |
| Username | 24px, 700, `textPrimary` |
| Handle | 15px, 400, `textSecondary` |
| Bio | 15px, 400, `textPrimary`, line-height 1.5 |
| Stats number | 15px, 700, `textPrimary` |
| Stats label | 13px, 400, `textSecondary` |
| Follow button | Primary, radius `full`, height 36-40px |
| Message/Share | Secondary (border), radius `full` |
| Tab height | 48px |
| Tab active | `textPrimary`, 2-3px underline `primary` |
| Tab inactive | `textTertiary` |

### 8.9 Loading States

**Skeleton:**
```typescript
// Animation
@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}

.skeleton {
  background: linear-gradient(90deg, #F0F0F0 25%, #E0E0E0 50%, #F0F0F0 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  border-radius: 4px;
}
```

| Component | Skeleton Shape |
|-----------|---------------|
| Avatar | Circle (matching avatar size) |
| Post title | Rectangle, 60% width, height 20px |
| Post excerpt | 2 rectangles, 100% and 80% width, height 14px |
| Post image | Rectangle, 100% width, height 200px, radius `lg` |
| Comment bubble | Rectangle with `xl` radius, 70% width |
| Stats number | Rectangle, 40px wide, height 16px |

**Spinner:**
- Size: 24px (default), 16px (small), 32px (large)
- Color: `primary`
- Stroke: 2px

### 8.10 Empty States

```
┌─────────────────────────────┐
│                             │
│       [Illustration]        │
│                             │
│     No posts yet            │
│  When posts are published,  │
│  they'll appear here.       │
│                             │
│     [Browse Discover]       │
│                             │
└─────────────────────────────┘
```

| Property | Value |
|----------|-------|
| Illustration size | 120×120px |
| Title | H3 (18px, 600, `textPrimary`) |
| Description | Body (15px, 400, `textSecondary`) |
| CTA button | Primary or Ghost |
| Padding | 48px vertical |
| Alignment | Center |

### 8.11 Error States

```
┌─────────────────────────────┐
│                             │
│       [Error Icon]          │
│                             │
│     Something went wrong    │
│  We couldn't load the feed. │
│                             │
│       [Try Again]           │
│                             │
└─────────────────────────────┘
```

| Property | Value |
|----------|-------|
| Icon | 48px, `error` color |
| Title | H3 (18px, 600, `textPrimary`) |
| Description | Body (15px, 400, `textSecondary`) |
| Retry button | Primary |
| Padding | 32px vertical |

### 8.12 Toast/Snackbar

```
┌──────────────────────────────────────┐
│ ✓  Post published successfully  [✕] │
└──────────────────────────────────────┘
```

| Property | Value |
|----------|-------|
| Position (mobile) | Top, full width minus 16px margin |
| Position (desktop) | Bottom-right |
| Background | `textPrimary` (dark: `backgroundTertiary`) |
| Text | `background` (dark: `textPrimary`) |
| Radius | `md` (8px) |
| Padding | 12px 16px |
| Duration | 3s (success/info), 5s (error) |
| Max width | 400px |
| Shadow | `lg` |

**Types:**
- Success: ✓ icon, `success` accent
- Error: ✕ icon, `error` accent
- Warning: ⚠ icon, `warning` accent
- Info: ℹ icon, `info` accent

### 8.13 Modal/Dialog

| Property | Mobile | Desktop |
|----------|--------|---------|
| Type | Bottom sheet | Center modal |
| Animation | Slide up | Fade + scale |
| Duration | 300ms | 200ms |
| Overlay | `overlay` (0.4 opacity) | `overlay` (0.4 opacity) |
| Radius | `lg` (top corners only) | `md` (all corners) |
| Max width | 100% | 480px |
| Max height | 90vh | Auto |
| Padding | 24px | 24px |

### 8.14 Dropdown/Popover

| Property | Value |
|----------|-------|
| Background | `background` |
| Border | 1px `border` |
| Radius | `md` (8px) |
| Shadow | `md` |
| Min width | 200px |
| Item height | 40px |
| Item padding | 0 12px |
| Item hover | `backgroundSecondary` |
| Item active | `primarySurface` |
| Divider | 1px `border` |
| Animation | Fade + scale, 150ms |

---

## 9. Animation System

### 9.1 Duration Tokens

| Token | Duration | Usage |
|-------|----------|-------|
| `instant` | 0ms | State changes |
| `fast` | 150ms | Hover states, tooltips |
| `normal` | 250ms | Button press, small transitions |
| `slow` | 350ms | Page transitions, modals |
| `deliberate` | 500ms | Hero animations, onboarding |

### 9.2 Easing Tokens

| Token | Curve | Usage |
|-------|-------|-------|
| `standard` | `cubic-bezier(0.4, 0, 0.2, 1)` | Default motion |
| `decelerate` | `cubic-bezier(0, 0, 0.2, 1)` | Entering elements |
| `accelerate` | `cubic-bezier(0.4, 0, 1, 1)` | Exiting elements |
| `spring` | `cubic-bezier(0.34, 1.56, 0.64, 1)` | Reactions, playful |

### 9.3 Specific Animations

**Comment/Chat Bubble Entrance:**
```css
@keyframes bubbleSlideIn {
  from {
    opacity: 0;
    transform: translateY(16px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
.comment-bubble {
  animation: bubbleSlideIn 250ms cubic-bezier(0, 0, 0.2, 1);
}
```

**Like/Reaction:**
```css
@keyframes heartBeat {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.25); }
}
.like-button.active {
  animation: heartBeat 300ms cubic-bezier(0.34, 1.56, 0.64, 1);
  color: #F4212E;
}
```

**Thread Expand/Collapse:**
```css
.thread-content {
  transition: max-height 250ms cubic-bezier(0.4, 0, 0.2, 1),
              opacity 200ms cubic-bezier(0.4, 0, 0.2, 1);
}
```

**Page Transition (Mobile):**
```css
.page-enter {
  animation: slideInRight 350ms cubic-bezier(0, 0, 0.2, 1);
}
.page-exit {
  animation: slideOutLeft 350ms cubic-bezier(0.4, 0, 1, 1);
}
```

**Skeleton Shimmer:**
```css
@keyframes shimmer {
  0% { background-position: -200% 0; }
  100% { background-position: 200% 0; }
}
.skeleton {
  animation: shimmer 1.5s infinite linear;
}
```

**Pull to Refresh:**
- Spring animation on release
- Spinner rotation: 360° in 800ms, linear
- Success: checkmark scale 0→1, spring easing

---

## 10. Gesture Interactions

### 10.1 Mobile Gestures

| Gesture | Context | Action |
|---------|---------|--------|
| Tap | Post card | Open post detail |
| Tap | Comment bubble | Open thread / expand replies |
| Tap | Avatar | Open user profile |
| Double tap | Post image | Like post |
| Double tap | Chat bubble | Like message |
| Long press | Comment | Context menu (reply, copy, share, report) |
| Long press | Chat message | Context menu (reply, copy, forward, delete, react) |
| Swipe right | Chat list item | Archive / mark read |
| Swipe left | Chat list item | Delete |
| Swipe right | Comment | Quick reply |
| Swipe left | Comment | Quick like |
| Pull down | Feed, Discover, Chat list | Refresh |
| Pinch | Post image | Zoom image |
| Swipe back | Any page | Navigate back |

### 10.2 Desktop Keyboard Shortcuts

| Key | Action | Context |
|-----|--------|---------|
| `/` | Focus search | Global |
| `N` | New post | Authenticated |
| `R` | Reply to comment | Comment focused |
| `L` | Like comment/post | Comment/post focused |
| `C` | Copy comment text | Comment focused |
| `↑` | Edit own comment | Own comment focused |
| `Esc` | Close modal / blur | Global |
| `J` | Next item in feed | Feed focused |
| `K` | Previous item in feed | Feed focused |
| `Enter` | Open selected item | Feed focused |
| `?` | Show keyboard shortcuts | Global |

---

## 11. Accessibility (WCAG 2.1 AA)

### 11.1 Color Contrast

| Combination | Ratio | Status |
|-------------|-------|--------|
| `textPrimary` on `background` | 16.1:1 | ✅ AAA |
| `textSecondary` on `background` | 5.7:1 | ✅ AA |
| `textTertiary` on `background` | 3.5:1 | ❌ (use for decorative only) |
| `primary` on `background` | 4.5:1 | ✅ AA |
| `white` on `primary` | 3.4:1 | ❌ (use for large text only) |

### 11.2 Focus States

```css
.focus-visible {
  outline: 2px solid #1D9BF0;
  outline-offset: 2px;
  border-radius: 2px;
}
```

- All interactive elements must have visible focus
- Focus ring: 2px `primary`, 2px offset
- Skip to content link (hidden until focused)

### 11.3 Screen Reader Support

- ARIA labels on all icon buttons
- Live regions for real-time updates (notifications, comments)
- Semantic HTML (nav, main, article, aside)
- Heading hierarchy (h1 → h2 → h3)
- Alt text on all images

### 11.4 Touch Targets

- Minimum: 44×44px (iOS HIG) / 48×48dp (Material)
- Spacing between targets: 8px minimum
- Exception: inline text links (must be at least 24px tall)

---

## 12. Dark Mode

### 12.1 Toggle Behavior

- Default: system preference (`prefers-color-scheme`)
- Manual override: persisted in localStorage (web) / SharedPreferences (mobile)
- Toggle location: navbar dropdown (web), settings screen (mobile)

### 12.2 Transition

```css
.theme-transition {
  transition: background-color 200ms ease,
              color 200ms ease,
              border-color 200ms ease;
}
```

- Smooth transition on theme change (200ms)
- No transition on initial page load

### 12.3 Images & Media

- Images: no adjustment (original colors preserved)
- Avatars: no adjustment
- Logos: provide dark variant if needed
- Code blocks: GitHub Dark theme

---

## 13. Micro-interactions

| Interaction | Feedback |
|-------------|----------|
| Button press | Scale 0.97, 100ms |
| Toggle switch | Slide animation, 200ms |
| Checkbox | Checkmark draw animation, 200ms |
| Bookmark | Fill animation, spring easing |
| Follow button | Text change + color change, 150ms |
| Tab switch | Underline slide, 250ms |
| Dropdown open | Fade + scale, 150ms |
| Modal open | Overlay fade + content slide/fade |
| Toast appear | Slide in from edge, 250ms |
| Toast dismiss | Slide out, 200ms |
| Infinite scroll trigger | Skeleton appear, shimmer starts |
| Image load | Fade in, 200ms |
| Avatar load | Placeholder → image crossfade, 300ms |

---

## 14. Onboarding Flow

### 14.1 First Launch (Mobile)

```
Screen 1: Welcome
  [Xilo Logo]
  "Welcome to Xilo"
  "Read, write, and discover ideas."
  [Get Started]

Screen 2: Sign Up / Login
  [Email/Phone input]
  [Continue]
  "or"
  [Sign in with Google] [Sign in with GitHub]

Screen 3: Interests
  "What are you interested in?"
  [Tech] [Science] [Business] [Art] [More...]
  [Continue]

Screen 4: Follow Suggestions
  "Follow writers you like"
  [@alice ✓] [Follow]
  [@bob] [Follow]
  [@charlie ✓] [Follow]
  [Continue]

Screen 5: Home Feed
  "You're all set!"
  [Start Reading]
```

### 14.2 Tooltips (First Visit)

- Write button: "Create your first post"
- Discover tab: "Find interesting discussions"
- Chat icon: "Message other users"
- Profile: "Customize your profile"

---

## 15. Error Handling UX

### 15.1 Network Errors

| Scenario | UX |
|----------|-----|
| No internet | Offline banner at top, cached content shown |
| Timeout | "Request timed out. Try again." + retry button |
| Server error (5xx) | "Something went wrong. We're working on it." + retry |
| Not found (404) | Custom 404 page with search and navigation |
| Unauthorized (401) | Redirect to login, preserve redirect URL |
| Forbidden (403) | "You don't have permission to access this." |

### 15.2 Form Validation

| Error | Message | Display |
|-------|---------|---------|
| Required | "This field is required" | Below input, `error` color |
| Invalid email | "Please enter a valid email" | Below input |
| Password too short | "Password must be at least 8 characters" | Below input |
| Username taken | "This username is already taken" | Below input |
| Slug taken | "This URL is already in use" | Below input |

**Inline validation:** On blur (not on every keystroke)
**Submit validation:** All fields validated before submit
**Submit button:** Disabled until form is valid
