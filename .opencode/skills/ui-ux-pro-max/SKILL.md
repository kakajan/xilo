---
name: ui-ux-pro-max
description: Xilo UI/UX design system for web and native Android. Use when choosing colors, typography, spacing, motion, touch targets, or layout patterns.
---

# UI/UX Pro Max (Xilo)

**Authority:** `openspec/changes/xilo-platform/specs/ui-ux-spec.md` — always read it before visual or interaction work. Do not invent conflicting tokens.

## Core tokens

| Token | Value |
|-------|-------|
| Primary | `#1D9BF0` |
| Touch target (Material) | **48×48 dp** minimum |
| Touch spacing | 8 dp between targets |
| Content-first | UI recedes; whitespace generous |

## Typography by locale

| Locale | Font |
|--------|------|
| Persian (`fa`, RTL) | **Vazirmatn** |
| Arabic (`ar`, RTL) | **Noto Sans Arabic** |
| LTR (`en`, `ru`, `tr`, …) | **Inter** |

Android: `xiloTypography(languageCode)` in `theme/Typography.kt`; `XiloTheme` passes `AppLocale.languageCode`.

## Motion

Use `theme/Motion.kt` (`XiloMotion`) — durations and easings match ui-ux-spec §9:

- Durations: instant 0, fast 150, normal 250, slow 350, deliberate 500 ms
- Easings: standard, decelerate, accelerate, spring (`CubicBezierEasing`)
- Like/reaction: `XiloMotion.heartBeat` (300 ms spring curve)

## Patterns to enforce

- Icon + title on **one row**, 8 dp gap — never stack icon above heading (§1.1).
- Haptics on like; double-tap image to like (§10.1).
- WCAG 2.1 AA contrast; visible focus ring 2 px primary.
- Meaningful motion only — no decorative animation loops on content.

## Android checklist

- [ ] `stringResource` for all user-visible copy (fa default + en/ar/ru/tr).
- [ ] `defaultMinSize(48.dp)` on icon action rows (feed, post detail).
- [ ] Coil images sized with `size` + `crossfade`.
- [ ] Dark mode via `XiloTheme` palette, not ad-hoc colors.

## Do not

- Use Tahoma/Arial as primary Persian font (Vazirmatn only).
- Use Vazirmatn for Arabic (Noto Sans Arabic).
- Hardcode `#1D9BF0` variants that contradict the spec palettes.
