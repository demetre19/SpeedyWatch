# SpeedyWatch Project DOX

## Purpose and Ownership

- This project owns the SpeedyWatch Android app: a YouTube WebView browser with native playback-rate controls, searchable caption transcripts, OpenRouter video summaries and pre-watch question guides, and best-effort DOM ad skipping.
- Keep source, generated APKs, and verification evidence inside this project boundary. Do not reuse release artifacts or private configuration from sibling Android projects.

## Product Contracts

- Support Android 10 and newer with package name `com.speedywatch.app`.
- Load YouTube only over HTTPS in the app WebView. YouTube navigation remains in-app; unrelated links open through Android's external handler.
- Keep WebView file access, content access, mixed content, popup windows, and WebView debugging disabled. Do not add a JavaScript bridge.
- `app/src/main/assets/speedywatch.js` is the single controller for playback speed, ad skipping, caption-track discovery, caption-request triggering, and timestamp seeking. Native code may call only its fixed methods with bounded numeric or boolean values and must not add a JavaScript bridge.
- Playback speed is bounded to 0.25x through 4x. The native controls provide common presets, 0.1x adjustments, and direct decimal entry; the controller restores the chosen rate when YouTube replaces or resets media elements.
- Ad skipping defaults to on. It removes known feed-ad DOM nodes and uses YouTube ad classes, skip controls, media seeking, and player fallbacks derived from the local GMB blocker. This is best effort because YouTube DOM and private player methods can change; it is not a network-level blocker.
- The compact top toolbar uses icon-only home, history, reload, YouTube Subs, Quiz, and Settings actions. Settings remains the rightmost action; do not restore the removed copy-URL or app-fullscreen toolbar actions unless the product requirement changes.
- The YouTube Subs modal loads timedtext JSON, filters captions case-insensitively, seeks through the fixed controller method, and closes after a timestamp selection. Summary results render common Markdown as readable native styled text. Prefer the signed track exposed by the page controller; when the mobile page omits it, use the no-key Android InnerTube player response as an unofficial fallback and accept only HTTPS YouTube `/api/timedtext` URLs.
- OpenRouter settings dynamically load text-output models from `https://openrouter.ai/api/v1/models`, prefer `inception/mercury-2` when available, and keep Summary One, Summary Two, and Quiz prompts editable. The API key field defaults to masked, exposes a short prefix/suffix check, and has an explicit visibility toggle. Persist the API key only as Android Keystore AES-GCM ciphertext; never log, export, or embed it.
- The Quiz action uses the active video's transcript to create one modal containing exactly 6, 10, 12, or 20 important pre-watch questions, each with a short description and no answer.
- The launcher icon is an adaptive dark/red speed, play, and watch mark with a legacy fallback.

## Build and Verification

- Use JDK 17 and the checked-in Gradle 9.4.1 wrapper. On this workstation set `JAVA_HOME=/Volumes/TheHoneyBadger/AndroidTooling/jdks/jdk-17.0.19+10/Contents/Home`, `ANDROID_HOME=/Volumes/TheHoneyBadger/AndroidTooling/android-sdk`, and `ANDROID_SDK_ROOT=$ANDROID_HOME`.
- Build the debug APK with `./gradlew --no-daemon :app:assembleDebug`.
- Install with `./gradlew --no-daemon :app:installDebug` while an emulator or phone is connected.
- The existing `cmuxPhoneApi36` AVD is the primary local smoke-test profile. Verify that YouTube renders, direct 2.7x entry and 0.1x stepping report matching live media rates, the ad-skipping toggle changes controller state, Settings loads the live OpenRouter catalog with Mercury 2 selected and exposes the masked API-key check and editable Quiz prompt, a captioned `/watch` video supports transcript search, timestamp seek with modal dismissal, readable Summary One and timestamped Summary Two, and Quiz generates exactly the selected 6, 10, 12, or 20 pre-watch questions with descriptions.
- Run `node --check app/src/main/assets/speedywatch.js` after changing the injected controller, then build and install the APK for behavioral changes.
- Every meaningful contract, structure, permission, toolchain, or verification change requires a DOX pass in this file and, when the child boundary changes, the workspace parent index.
