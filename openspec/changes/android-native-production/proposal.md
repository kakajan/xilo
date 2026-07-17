# Proposal: Android Native Production

**Status:** Proposed  
**Date:** 2026-07-16  
**Owner:** Xilo Team

## Summary

Establish `android/` as Xilo's sole active mobile client and deliver it through a controlled in-place refactor of the existing Android project. The production client SHALL use Kotlin and Jetpack Compose; the existing `mobile/` Flutter project is preserved only as legacy historical material and is explicitly out of scope.

## Motivation

The platform guidance currently contains competing Flutter and Kotlin directions. This change removes that ambiguity, defines a production-quality Android contract, and creates traceable implementation gates without claiming parity based on Flutter or prototype work.

## Scope

In scope:
- Native Android application architecture, parity requirements, offline synchronization, release quality, and CI requirements.
- Alignment of active `xilo-platform` and `multilingual-support` OpenSpec guidance with the Android-only decision.
- A controlled in-place refactor of `android/`, rather than creating a replacement application.

Out of scope:
- Deleting, migrating, building, testing, or maintaining `mobile/`.
- iOS, Flutter, desktop, backend, web, infrastructure, or CI implementation changes.
- Altering shared API behavior except where an approved later change explicitly requires it.

## Decisions

1. `android/` is the sole supported mobile application.
2. Its application ID is `ir.xilo.app` and its minimum supported SDK is API 24.
3. Kotlin, Jetpack Compose, Hilt, Room, Retrofit, OkHttp, Paging 3, WorkManager, DataStore, and Android Keystore are mandatory platform foundations.
4. Shared behavior remains defined by the existing domain specifications. Android requirements reference those contracts instead of duplicating server rules.
5. Existing Flutter artifacts are legacy/out of scope, not evidence that Android tasks are complete.

## Success Criteria

- [ ] No active OpenSpec or repository guidance directs implementation work to Flutter.
- [ ] Android parity requirements cover all listed product domains with testable scenarios.
- [ ] Implementation tasks have unambiguous, unchecked acceptance gates.
- [ ] The Android project can be refactored in place without changing its application identity or losing required source-control history.

## Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Prototype code is mistaken for production readiness | Every Android task remains unchecked until its stated evidence gate passes. |
| Client/server contract gaps block parity | Contract gaps are recorded as explicit dependencies and resolved through separate shared-domain changes. |
| Offline mutations duplicate actions | Use durable operation IDs, ordered outbox processing, and idempotent server mutation contracts. |
| RTL regressions | Require locale and RTL Compose tests before release gates. |
