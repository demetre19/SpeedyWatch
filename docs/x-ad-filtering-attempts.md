# X (Twitter) Sponsored-Post Filtering — Attempted and Not Working

Status: **Abandoned.** None of the approaches below reliably hid sponsored posts
in the Android WebView. Every build passed its unit-style checks on synthetic
DOM, yet promoted posts remained visible on the real mobile X timeline.

## Context

- Target: `app/src/main/assets/speedywatch.js` (Android WebView controller).
- Goal: hide the entire promoted post (`display: none` on the enclosing
  `[data-testid="cellInnerDiv"]`) when X shows an exact `Ad` / `Promoted` /
  `Sponsored` marker, without touching organic posts or other sites.
- Verification route: local Android emulator + Chromium DevTools protocol, plus
  real-device installs over Tailscale/Termux. The user could not stay signed in
  to X in the emulator, so real-feed verification always happened on the phone.

## What was tried (and failed)

1. **Exact-label match, hide whole post.** Matched a visible span whose
   normalized text is exactly `Ad`, `Promoted`, or `Sponsored` inside the
   timeline post, then hid the enclosing cell. Result: ads stayed visible; the
   marker was never matched on live mobile markup.

2. **Geometry guard.** Restricted the label match to the post's upper-right
   header region using `getBoundingClientRect` comparisons. Result: made the
   matcher stricter without fixing detection; ads still visible.

3. **Header-row structural anchor (caret within 3 ancestor levels).** Required
   the marker to share a row with `[data-testid="caret"]`. Result: too
   restrictive for real markup depth; ads still visible.

4. **Loosened caret requirement.** Kept the exact-label rule, rejected markers
   inside `[data-testid="tweetText"]`, and only required the post to contain a
   caret menu. Result: synthetic fixtures hid correctly, live ads did not.

5. **Periodic rescans.** Added 500 ms ticks plus forced catch-up sweeps at 1 s,
   3 s, and 5 s after load, and a scroll listener that sweeps once scrolling
   settles (max one sweep/second, skipped mid-scroll). Result: cadence was
   irrelevant because detection itself never matched live ads.

6. **Insertion-driven scanning (ported from the open-source
   `twitter_cleaner` extension).** Checked each `[data-testid="cellInnerDiv"]`
   the moment the MutationObserver saw it added; added the extension's
   structural signal `[data-testid="placementTracking"]`, which X renders only
   in promoted posts on desktop. Result: ads still visible on mobile — the
   mobile timeline apparently does not expose either the expected label spans
   or `placementTracking` in the rendered DOM.

7. **Positional hiding (user-requested heuristic).** Hid every 5th timeline
   cell starting with the 2nd (cells 2, 7, 12, 17, …) unconditionally,
   recomputing ordinals from current DOM order on every sweep. Result: still
   nothing hidden — even force-hiding by index produced no visible change on
   the phone, which suggests the remaining gap is not selector logic but
   something about how/when the script runs against the live mobile feed
   (e.g., script injection timing, site shell/caching, or a different runtime
   document than the one being scanned).

## What did work

- Non-ad behavior was preserved throughout: YouTube filtering and playback were
  untouched (verified in the emulator), and all synthetic-DOM tests (exact
  marker hidden, organic text mentioning "ad" preserved, non-X sites exempt)
  passed in every build.
- Tooling: local emulator + `adb` + DevTools-protocol inspection, and the
  Tailscale/Termux upload path (`~/storage/downloads/SpeedyWatch-latest.apk`,
  SHA-256 verified, `termux-open` for manual install) worked reliably.

## State of the code

The final build (`eefbce5f…`) still contains the full filter stack: label
matching, `placementTracking`, insertion-driven scans, settled-scroll sweeps,
1/3/5-second catch-ups, and the every-5th-cell positional rule. None of it
visibly affected the live feed. The change is uncommitted; the branch
`codex/x-sponsored-post-filtering` still only holds the iOS X-client work and
the earlier Android scaffolding from PR #4.

## Lessons for a future attempt

- Synthetic DOM tests proved nothing about the live mobile feed; verification
  requires a signed-in X session, which the emulator never had.
- Desktop-derived signals (`Promoted` text, `placementTracking`) did not
  transfer to mobile web.
- Before more selector work, confirm which document actually runs
  `speedywatch.js` on the phone and dump the live `cellInnerDiv` HTML from that
  document (e.g., via remote debugging on the device itself) — the mismatch
  between "hidden in fixtures" and "untouched on device" points there.
