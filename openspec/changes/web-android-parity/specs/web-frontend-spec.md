# Delta Spec: Web Frontend — Android Parity

## REQ-WEB-PARITY-001: Four-tab consumer shell

**Given** an authenticated (or guest) user on a consumer route  
**When** they view the app on mobile viewport  
**Then** they see a floating pill bottom navigation with Feed, Discover, Chat, Profile  
**And** Feed shows a FAB to create a post  
**And** dashboard/write routes do not show this nav.

## REQ-WEB-PARITY-002: Discover

**Given** a user on `/discover`  
**When** the page loads  
**Then** they see recent comment cards (Android-equivalent behavior)  
**And** can search and open a parent post thread.

## REQ-WEB-PARITY-003: Chat

**Given** an authenticated user on `/chat`  
**When** they open a conversation  
**Then** they can list chats, send/receive messages via `/api/chats`, and open Saved Messages / Saved Hub.

## REQ-WEB-PARITY-004: Profile & social

**Given** a user on `/[username]`  
**When** the profile loads  
**Then** they see stats, Follow/Message actions, and Posts/Replies/Likes tabs  
**And** can open followers/following lists.

## REQ-WEB-PARITY-005: Settings parity

**Given** a user on `/settings`  
**When** they open the menu  
**Then** they can access calendar, devices, chat folders, saved, avatar change, and logout.

## REQ-WEB-PARITY-006: Visual tokens & RTL

**Given** the consumer UI  
**When** rendered  
**Then** primary `#1D9BF0`, dark background `#15202B`, Vazirmatn for Persian, and RTL layout apply  
**And** comment/chat bubbles use `bubble_own` / `bubble_others` tokens.
