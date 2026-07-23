# Design System

## Overview

SpeedyWatch is a compact, content-first iPhone utility. YouTube content occupies the primary surface; native controls remain dense, familiar, and subordinate. The visual system preserves the Android app's established dark interface and restrained red active state while using standard iOS navigation, sheets, typography, and accessibility behavior.

## Theme

- Appearance: dark.
- Background: `#0F0F0F`.
- Secondary surface: `#1E1E1E`.
- Control surface: `#303030`.
- Primary text: `#FFFFFF`.
- Secondary text: `#B9B9B9`.
- Active/accent: `#FF0033`.
- Destructive: system red.
- Maintain at least WCAG AA contrast for text and state labels.

## Typography

Use San Francisco through SwiftUI system text styles. Respect Dynamic Type. Use semibold weight for section labels and active values; use monospaced digits only for timestamps and playback rates where alignment improves scanning.

## Layout

- Respect all iPhone safe areas.
- Keep the compact icon toolbar above the WebView.
- Let the WebView consume all flexible vertical space.
- Keep playback presets and adjustments in a bottom control panel.
- Use native sheets with navigation titles for Transcript, Quiz, Saved, and Settings.
- Use at least 44-point touch targets.

## Components

- Toolbar actions: SF Symbols, icon-only, accessible labels and hints.
- Playback presets: compact buttons; the selected speed uses the active red fill.
- Status: visible text for rate and ad state; never rely on color alone.
- Forms: native text fields, secure fields, pickers, toggles, and buttons.
- Loading: inline progress with descriptive status text.
- Errors: concise native alerts with actionable messages.
- Empty states: explain which video or setting is required.
- Markdown: render with `AttributedString(markdown:)`, falling back to plain text.
- AI chat: keep a compact text field and Send action at the bottom of a generated summary; render prior questions and answers in the summary scroll.
- Saved output actions: expose Save and native Share actions only after successful summary or quiz generation, with save confirmation through native alerts.

## Motion

Use only native sheet, navigation, and state transitions. Honor Reduce Motion automatically through SwiftUI. Do not add decorative animation.

## Accessibility

Provide accessibility labels for every icon, support Dynamic Type, preserve VoiceOver reading order, pair every color state with text, and avoid fixed-height text containers that clip enlarged content.
