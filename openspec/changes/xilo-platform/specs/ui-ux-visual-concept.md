# UI/UX Visual Concept Specification: Hybrid Interface

## Overview
This document details the visual UI/UX concepts for the Xilo application, based on the hybrid design that combines the content-first feed structure of Twitter/X with the aesthetic and conversational elements of Telegram. 

## 1. Profile & Feed Interface (Twitter × Telegram Hybrid)

### 1.1 Top Navigation Bar
*   **Left Action:** Back chevron (`<`) inside a minimal circular tap target.
*   **Center Brand:** App Logo (a hybrid of a blue paper plane and an 'X').
*   **Right Action:** Three-dot ellipsis (`...`) for 'More' options inside a circular container.

### 1.2 User Profile Section
*   **Avatar:** Large, centered circular profile picture (approx. 100-120px) positioned just below the navigation bar.
*   **Identity:** 
    *   **Display Name:** Centered, bold text (e.g., "Alex Morgan") accompanied by a solid blue verified tick.
    *   **Handle:** Centered secondary text (e.g., "@alexmorgan") placed directly below the name.
*   **Bio:** Centered, legible paragraph text conveying the user's description (e.g., "Building the future at the intersection of technology, design, and human potential.").
*   **Stats Layout:** Three-column grid displaying:
    *   "Posts" (e.g., 1,248)
    *   "Followers" (e.g., 256K)
    *   "Following" (e.g., 1,023)
    *   *Note: Numbers are bold and placed directly above their respective labels.*
*   **Action Buttons Row:** 
    *   **Primary Action ("Follow"):** Solid blue background, white text, and an 'add person' icon.
    *   **Secondary Action ("Message"):** Outline style, black text, and a chat bubble icon.
    *   **Tertiary Action ("Share Profile"):** Outline style, black text, and an upload/share icon.

### 1.3 Feed Navigation (Tabs)
*   **Tabs:** "Posts", "Replies", "Media", "Likes".
*   **Active State:** Primary blue text with a pill-shaped, floating blue indicator below the active tab.
*   **Inactive State:** Gray secondary text.

### 1.4 Post & Thread Feed
*   **Parent Post (Twitter Style):**
    *   **Header:** Avatar on the left, Name, Handle, and timestamp (e.g., "· 2h") on the top row.
    *   **Content:** Text content (supporting emojis) followed by media attachments (images feature rounded corners, approx. 12-16px radius).
    *   **Action Bar:** Spaced icons with counts for Comment (e.g., 128), Retweet/Share (e.g., 256), Like (e.g., 1.8K in pink), Share, and Analytics.
*   **Thread/Replies (Telegram Style Bubbles):**
    *   A subtle, continuous vertical thread line connects the parent post's avatar downward to the replies' avatars.
    *   **Reply Bubbles:** 
        *   Replies are encased in rounded chat bubbles with an internal padding of ~12-14px.
        *   **Color Differentiation:** Per the authority in `ui-ux-spec.md`, the profile owner's own replies use light blue `#E8F5FE` (`#1E3A5F` dark), while other commenters use gray `#F7F9FA` (`#2C2C2E` dark).
    *   **Bubble Content:**
        *   Text of the reply.
        *   **Reaction Pills:** Telegram-style reaction pills attached to the bottom-left inside the bubble (e.g., "🔥 12", "💯 3"). The pills have subtle background tints corresponding to the emoji.
        *   **Timestamp:** Small, secondary text (e.g., "9:15 AM") located at the bottom-right corner inside the bubble.

---

## 2. Chat & Messaging Interface (Telegram Style)

### 2.1 Chat List Screen
*   **Background Theme:** Light blue gradient featuring a subtle, tiled paper plane watermark pattern.
*   **Stories / Active Users Row:**
    *   Located at the very top, a horizontal scrollable list of circular user avatars.
    *   Avatars feature colorful gradient rings indicating unseen stories.
    *   The first item is an "Add Story" button with the user's avatar and a blue '+' overlay badge.
*   **Categories / Filter Tabs:**
    *   Scrollable horizontal text tabs placed below the stories: "All Chats", "New", "Family", "Church", "Work", "People".
    *   The active tab ("All Chats") is bolded with a primary blue underline.
*   **Chat List Items:**
    *   **Left Element:** Circular user avatar or a specialized icon (e.g., blue bookmark icon for "Saved Messages").
    *   **Top Row:** Contact Name (bold, dark text) and Timestamp (e.g., "10:42 PM", aligned to the right edge).
    *   **Bottom Row:** Message preview (truncated if too long).
    *   **Status Indicators:** Unread Badge (solid blue circle with white number) or Read Receipt (two green checkmarks) located on the right. Pinned chats feature a small pin icon.
*   **Floating Bottom Navigation:**
    *   A floating, elevated pill-shaped container holding three main tabs: "Contacts", "Chats", and "Settings".
    *   **Active Tab Design:** The active tab ("Chats") features a light gray/blue circular background behind its icon and blue text, making it distinct from the inactive gray icons.

### 2.2 Contact / Profile Detail Screen
*   **Hero Image Header:** 
    *   A large, edge-to-edge profile photograph occupying the top half of the screen, featuring rounded top corners.
    *   **Top Action Bar (Overlaid):** Translucent dark circular buttons for Back `<` and `Edit` at the top left and right.
    *   **Identity Info (Overlaid):** Name (e.g., "Charlotte Harris") and status (e.g., "last seen recently") placed at the bottom left of the image gradient overlay.
*   **Quick Action Row:**
    *   Located overlapping the bottom edge of the hero image.
    *   Five translucent dark circular buttons: "call" (phone icon), "video" (camera icon), "mute" (bell icon), "search" (magnifying glass), "more" (three dots).
    *   Labels are placed directly below each icon in small, white text.
*   **Details & Content Lists:**
    *   **Channel Section:** Displays subscribed channels (e.g., "Channel", "2.7K subscribers"). Channel preview cards feature a rounded square icon, title, timestamp, and a preview of the latest post.
    *   **Contact Info Section:** Displays "mobile" label with the phone number formatted in interactive primary blue (e.g., "+375 33 123 45 67").
