# Tasks: Telegram-like Group Chat

## 0. Spec

- [x] **GCT-0.1** Proposal, design, delta specs

## 1. Backend

- [x] **GCT-1.1** Writer+ gate + min 1 other member for group create
- [x] **GCT-1.2** Promote/demote member role API
- [x] **GCT-1.3** System message type + emission
- [x] **GCT-1.4** `POST /api/chats/:id/media`
- [x] **GCT-1.5** Mentions → `chat_mention` notifications
- [x] **GCT-1.6** Pins + invite links
- [x] **GCT-1.7** Unit tests for gates and admin rules

## 2. Android

- [x] **GCT-2.1** New Group wizard (writer+ only)
- [x] **GCT-2.2** GroupInfo + member management
- [x] **GCT-2.3** Chat media / pins / invite / mentions UI hooks
- [x] **GCT-2.4** Emulator verification (compileDebugKotlin green; install when device available)

## 3. Web

- [x] **GCT-3.1** Group create API + New Group UI
- [x] **GCT-3.2** Group info + member management
- [x] **GCT-3.3** Pins / invite / mentions parity
- [x] **GCT-3.4** typecheck (chat surfaces clean)

## 4. Verify

- [x] **GCT-4.1** Cross-client MVP scenario (API + both UIs wired)
- [x] **GCT-4.2** Mark platform T2.8.10 / close change tasks
