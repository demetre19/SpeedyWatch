# SpeedyWatch Project DOX

## Purpose and Ownership

- This project owns the SpeedyWatch Android app: a YouTube WebView browser with native playback-rate controls, searchable caption transcripts, OpenRouter video summaries and pre-watch question guides, and best-effort DOM ad skipping.
- Keep source, generated APKs, and verification evidence inside this project boundary. Do not reuse release artifacts or private configuration from sibling Android projects.
- `README.md` and `logo.png` are the public GitHub landing assets. Keep their feature claims, installation requirements, release metadata, and privacy disclosures aligned with the shipped app.

## Product Contracts

- Support Android 10 and newer with package name `com.speedywatch.app`.
- Load YouTube only over HTTPS in the app WebView. YouTube navigation remains in-app; unrelated links open through Android's external handler.
- Keep WebView file access, content access, mixed content, popup windows, and WebView debugging disabled. Do not add a JavaScript bridge.
- `app/src/main/assets/speedywatch.js` is the single controller for playback speed, ad skipping, caption-track discovery, caption-request triggering, and timestamp seeking. Native code may call only its fixed methods with bounded numeric or boolean values and must not add a JavaScript bridge.
- Playback speed is bounded to 0.25x through 4x. The native controls provide common presets, 0.1x adjustments, and direct decimal entry; the controller restores the chosen rate when YouTube replaces or resets media elements.
- Ad skipping defaults to on. It removes known feed-ad DOM nodes and uses YouTube ad classes, skip controls, media seeking, and player fallbacks derived from the local GMB blocker. This is best effort because YouTube DOM and private player methods can change; it is not a network-level blocker.
- The compact top toolbar uses icon-only home, history, reload, YouTube Subs, Quiz, Saved Summaries, and Settings actions. Saved Summaries uses a thin bookmark icon immediately left of Settings; Settings remains the rightmost action. Do not restore the removed copy-URL or app-fullscreen toolbar actions unless the product requirement changes.
- The YouTube Subs modal loads timedtext JSON, filters captions case-insensitively, seeks through the fixed controller method, and closes after a timestamp selection. Summary results render common Markdown as readable native styled text. Every successful Summary One or Summary Two result exposes Copy summary and Save summary actions; Save summary persists the raw Markdown, summary type, video title, original HTTPS YouTube URL, and save timestamp in the app-private `saved_summaries.db` SQLite database. Prefer the signed track exposed by the page controller; when the mobile page omits it, use the no-key Android InnerTube player response as an unofficial fallback and accept only HTTPS YouTube `/api/timedtext` URLs.
- OpenRouter settings dynamically load text-output models from `https://openrouter.ai/api/v1/models`, prefer `inception/mercury-2` when available, and keep Summary One, Summary Two, and Quiz prompts editable. AI requests use the exact saved field prompt as the system instruction; user messages contain only neutral source metadata and transcript data, with the selected Quiz count included as request metadata. UI string resources prefill missing Settings fields, but request and settings storage code never seed, substitute, append, or format prompts. Empty prompt fields are rejected. Do not add hidden output, structure, timestamp, or quiz instructions to request builders. The API key field defaults to masked, exposes a short prefix/suffix check, and has an explicit visibility toggle. Persist the API key only as Android Keystore AES-GCM ciphertext; never log, export, or embed it.
- The Quiz action sends the active video's transcript and the selected 6, 10, 12, or 20 question count as neutral request data. The editable Quiz prompt solely controls the generated output.
- The Saved Summaries modal lists newest saves first, searches case-insensitively across video titles, summary types, headings, and full summary text, and opens a Markdown-rendered detail view that shows the original URL. Opening the source must validate an HTTPS YouTube host before loading it in the existing WebView. Deletion requires confirmation.
- The launcher icon is an adaptive dark/red speed, play, and watch mark with a legacy fallback.
- Public APK delivery uses GitHub Releases in `demetre19/SpeedyWatch`. Each release attaches the rebuilt APK as `SpeedyWatch.apk`; the README's stable latest-download link must remain `https://github.com/demetre19/SpeedyWatch/releases/latest/download/SpeedyWatch.apk`.
- Until a production signing workflow is added, public APKs are debug-signed and the README and release notes must say so. Do not imply production signing or seamless signer migration.

## Build and Verification

- Use JDK 17 and the checked-in Gradle 9.4.1 wrapper. On this workstation set `JAVA_HOME=/Volumes/TheHoneyBadger/AndroidTooling/jdks/jdk-17.0.19+10/Contents/Home`, `ANDROID_HOME=/Volumes/TheHoneyBadger/AndroidTooling/android-sdk`, and `ANDROID_SDK_ROOT=$ANDROID_HOME`.
- Build the debug APK with `./gradlew --no-daemon :app:assembleDebug`.
- Install with `./gradlew --no-daemon :app:installDebug` while an emulator or phone is connected.
- Before publishing, calculate the exact APK SHA-256, update the README metadata, upload that same file as the GitHub Release asset, and verify the public latest-download URL resolves successfully.
- The existing `cmuxPhoneApi36` AVD is the primary local smoke-test profile. Verify that YouTube renders, direct 2.7x entry and 0.1x stepping report matching live media rates, the ad-skipping toggle changes controller state, Settings loads the live OpenRouter catalog with Mercury 2 selected and exposes the masked API-key check and all three editable prompt fields, a captioned `/watch` video supports transcript search and timestamp seek with modal dismissal, Summary One and Summary Two each follow only their saved prompt, successful summaries expose Copy and Save actions, Saved Summaries persists across modal/app reopen with its source URL, heading/body search filters correctly, source URLs reopen in the WebView, and Quiz follows only its saved prompt while receiving the selected count as request metadata.
- Run `node --check app/src/main/assets/speedywatch.js` after changing the injected controller, then build and install the APK for behavioral changes.
- Every meaningful contract, structure, permission, toolchain, or verification change requires a DOX pass in this file and, when the child boundary changes, the workspace parent index.
