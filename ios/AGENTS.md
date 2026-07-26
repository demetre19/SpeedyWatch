# SpeedyWatch iPhone DOX

## Purpose

Own the native iPhone port of SpeedyWatch with behavior parity to the Android application: in-app YouTube browsing, persistent playback speed, best-effort ad skipping, transcripts, OpenRouter summaries with follow-up chat, savable quiz guides, saved content, and settings.

## Ownership

- `SpeedyWatch/` contains Swift sources and bundled non-secret assets.
- `SpeedyWatch.xcodeproj` defines the application and test targets.
- `SpeedyWatchTests/` verifies boundary behavior that can run without YouTube or OpenRouter availability.
- `SpeedyWatchUITests/` owns the local Simulator parity flow and must not contain secrets.

## Local Contracts

- Support iOS 17 and newer with bundle identifier `com.speedywatch.ios`.
- Load YouTube only over HTTPS inside `WKWebView`; open unrelated hosts through the system.
- Enable inline media playback and JavaScript, but do not expose native objects through a script message bridge.
- Bound playback speed to 0.25x through 4x, expose Normal 1x, Careful 0.8x, Lecture 1.5x, and Podcast 2x profiles, and reapply the chosen baseline when YouTube replaces media elements. Optional adaptive speed adds 0.5x only during caption gaps and must return to the unchanged baseline.
- Keep the screen awake only while the app is active.
- Treat ad skipping as best effort and preserve the Android controller's ordered strategies.
- SponsorBlock community skipping is optional, defaults off, and remains separate from always-on YouTube ad skipping. Query only the privacy-preserving four-character SHA-256 prefix endpoint for enabled sponsor, self-promotion, and interaction categories; validate the exact active video and bounded `skip` segments before passing them to the shared controller. Each skip must offer a brief Undo action.
- Accept caption downloads only from HTTPS YouTube `/api/timedtext` URLs. Prefer the signed page track, then use the Android InnerTube player response fallback. Preserve manual and auto-generated tracks separately; the transcript reader supports language/track selection, line and paragraph views, case-insensitive search, full copy, follow-playback highlighting, and seek-close.
- Store the OpenRouter key in Keychain. For deterministic local Simulator verification, a non-empty DEBUG process environment key overrides Keychain; never log, bundle, or commit a key.
- Export and restore the shared versioned JSON backup through native document pickers. Include non-secret settings plus saved summaries and quizzes; never include the Keychain API key, cached summaries, or transient chat history. Validate and confirm before replacing local settings and saved content.
- Send the saved prompt unchanged as the OpenRouter system message. Initial user messages contain only source metadata, transcript data, and the selected quiz count. Summary follow-ups preserve the originating prompt and current conversation, adding only an explicitly labeled user-authored question.
- Show each OpenRouter model's advertised context length and normalized per-million-token prompt/completion prices, with Free and Long context filters that do not silently change the saved model.
- Persist saved summaries and quizzes in the same app-private collection, newest first. Validate source URLs before loading them.
- Expose native `ShareLink` actions for generated summaries, generated quizzes, and saved detail content only when the original source is a validated HTTPS YouTube URL. Share plain text containing the video title, content label, generated text, and original URL. The `SpeedyWatchShare` extension accepts shared text or URLs, queues only validated YouTube video URLs in the shared app group, and opens them in the existing app; unrelated or insecure URLs must not enter the WebView.
- Use native iOS controls, Dynamic Type, VoiceOver labels, and 44-point minimum touch targets.
- Public GitHub delivery is source-only under `ios/` until an Apple-signed distribution workflow exists. Do not publish or describe a simulator build or source archive as an installable iPhone download; physical-device builds require an Apple Development team.

## Work Guidance

- Prefer small SwiftUI views backed by focused observable services.
- Keep WebKit JavaScript in the shared `../app/src/main/assets/speedywatch.js` file as the single playback, adaptive-speed, ad, SponsorBlock, caption, current-time, and seeking controller bundled by the iPhone target.
- Do not add third-party dependencies when Foundation, SwiftUI, WebKit, Security, and XCTest suffice.

## Verification

- Build with `xcodebuild -project ios/SpeedyWatch.xcodeproj -scheme SpeedyWatch -sdk iphonesimulator -destination 'platform=iOS Simulator,name=iPhone 17 Pro' build`.
- Run unit tests for URL validation, transcript parsing, neutral initial and follow-up request construction, prompt/model non-seeding, and saved summary/quiz persistence.
- Live UI parity tests require `OPENROUTER_API_KEY` in the test-process environment. Locally, put only `OPENROUTER_API_KEY = …` in ignored `ios/LocalSecrets.xcconfig`, pass both `-derivedDataPath ios/DerivedData` and `-xcconfig ios/LocalSecrets.xcconfig`, and remove `ios/DerivedData` after the run. The shared scheme expands the build setting into the DEBUG process environment without adding it to a bundle. `xcodebuild` may echo build settings, so keyed logs and result bundles remain private.
- Install and launch on the local iPhone 17 Pro Simulator, then exercise home/back/forward/reload, shared-URL intake, direct 2.7x playback entry, 0.1x decrement/increment, persisted profiles, adaptive caption-gap return to baseline, always-on ad skipping, optional SponsorBlock category selection and Undo, key masking, live text-model loading with context/pricing and both filters, transcript track selection plus line/paragraph/search/copy/follow/seek behavior, backup export and validated restore, both summaries, Copy, Save, and Share actions, follow-up summary chat, saved summary/quiz search, source reopen, and Share action, all quiz counts, live quiz generation, and Save and Share quiz actions.

## Child DOX Index

No child contracts.
