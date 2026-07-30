<p align="center">
  <img src="logo.png" width="240" height="240" alt="SpeedyWatch logo">
</p>

<h1 align="center">SpeedyWatch</h1>

<p align="center">
  <strong>Watch more in less time.</strong><br>
  Control media playback across supported sites, search available transcripts, create and save readable summaries, and prepare with focused pre-watch questions.
</p>

## Download SpeedyWatch

| Android | iPhone |
| --- | --- |
| **Android 10 and newer** | **iOS 17 and newer** |
| [**Download the installable Android APK**](https://github.com/demetre19/SpeedyWatch/releases/latest/download/SpeedyWatch.apk) | [**Download the source ZIP**](https://github.com/demetre19/SpeedyWatch/archive/refs/heads/main.zip) |
| Current public APK: **v0.21**, debug-signed | Open the separate [`ios/` iPhone project](https://github.com/demetre19/SpeedyWatch/tree/main/ios) in Xcode |

### Samsung Galaxy: install the APK

**Developer Options and USB debugging are not required.**

1. Download `SpeedyWatch.apk`, then open **My Files → Downloads → SpeedyWatch.apk**.
2. If Samsung says the phone cannot install unknown apps from this source, tap **Settings** in that prompt and enable **Allow from this source** for the app opening the file, such as My Files, Chrome, or Samsung Internet. Return to the APK and tap **Install**.
3. If Samsung **Auto Blocker** prevents the install, open **Settings → Security and privacy → Auto Blocker**, turn it off only long enough to install this APK, then turn Auto Blocker back on.
4. Let [Google Play Protect](https://support.google.com/googleplay/answer/2812853) scan the APK if prompted. Keep Play Protect enabled; if it identifies the file as harmful rather than merely unknown, stop and report the exact warning.
5. Tap **Open** after installation, or launch **SpeedyWatch** from the app drawer.

If Android says **App not installed**, cannot update the existing app, or installs but will not open, confirm the phone runs Android 10 or newer. Then uninstall any older SpeedyWatch copy and install this APK again; an older copy signed with a different key cannot be updated in place. **Uninstalling removes that copy's app-private settings, saved summaries, and saved quizzes.**

> **iPhone availability:** the iPhone app is currently provided as source code for an Xcode build. There is no Apple-signed IPA, TestFlight, or App Store download yet.

[Release notes and previous Android downloads](https://github.com/demetre19/SpeedyWatch/releases/latest)

---

## What SpeedyWatch does

SpeedyWatch is a focused Android multi-site media browser and iPhone YouTube browser for people who want faster playback and useful transcript tools without leaving the video.

- On Android, use the site icon immediately after **Search** to choose YouTube, Bilibili, Instagram, Vimeo, X, Facebook, MEGA, or an external Web search. Keywords search services that support them; selecting MEGA opens a URL-only field for a complete public folder or file link. A strictly validated supported HTTPS media URL opens that exact page for playback and, where supported, captions, summaries, quizzes, or downloading.
- Open shared supported media links, including complete public MEGA folder/file links, directly in SpeedyWatch on Android. MEGA support is playback-only; the iPhone share extension remains YouTube-only.
- Set playback speed from **0.25x to 4x** with common presets, direct decimal entry, or **0.1x** adjustments.
- Choose a **Normal**, **Careful**, **Lecture**, or **Podcast** profile, and optionally add 0.5x only during caption gaps with adaptive speed.
- Keep the chosen baseline speed when a supported site replaces or resets its media element; adaptive YouTube caption-gap boosts never replace that saved baseline.
- Choose and persist a custom **default playback speed** in Settings for future app launches.
- Skip known YouTube ads and feed-ad elements on a best-effort basis. Ad skipping is inactive on other sites.
- Optionally skip YouTube community-submitted sponsor, self-promotion, and interaction segments from SponsorBlock, with a brief notice and Undo action after each skip.
- On Android, tap Download to use a valid copied media URL from YouTube, Bilibili, Instagram, Vimeo, X, or Facebook, or fall back to the current supported page. Choose MP3 audio or an MP4 up to an available resolution for that exact media item. Add more downloads while one is running and SpeedyWatch queues them in order, processes one at a time, and shows the waiting count in the notification. Downloads continue in the background, use the extracted title as the filename, and are written to `Downloads/SpeedyWatch`.
- Where the selected service exposes captions, choose among available languages and manual or auto-generated tracks, search in line or paragraph view, copy the transcript, and optionally follow the current playback position.
- Tap any transcript line or paragraph to jump to that moment and return to the video.
- Create two independently configurable summaries through OpenRouter, then ask follow-up questions in the same transcript view. Android renders each `You` turn in a padded, rounded dark-red bubble so it remains visually distinct from AI output.
- On Android, returning from a requested summary resumes the same video at the playback position captured when Summary One or Summary Two was pressed instead of restarting from the beginning.
- Successful Summary One and Summary Two results are cached automatically in app-private storage. Pressing the same Summary button again immediately renders the cached result without another OpenRouter request when the summary type, prompt, model, source URL, and transcript are unchanged.
- Save summaries and generated quiz guides locally with their original validated source URL, then search titles, types, headings, and body text from the bookmark library.
- Share a generated summary, generated quiz, or saved item through the platform's native share surface on Android and iPhone. Every share includes the original validated source URL.
- Export settings plus saved summaries and quizzes to a cross-platform JSON backup, then restore it on Android or iPhone. OpenRouter API keys are never included.
- Select **6, 10, 12, or 20** as request context for the editable Quiz prompt.
- Edit the Summary One, Summary Two, and Quiz prompts in Settings. These fields are the only source of AI output instructions. The live model picker shows context length and per-million-token input/output prices and can filter free or long-context models.
- On Android, check the official latest stable GitHub Release from Settings. SpeedyWatch downloads the APK, verifies its exact GitHub size and SHA-256, and opens Android's installer automatically while the app remains open, so there is no extra Downloads-folder step. Android still requires per-source permission and final Update or Install confirmation.
- Choose High (192 kbps), Standard (128 kbps), or Compact (64 kbps) for every Android MP3 download. Settings persists the default quality, and queued downloads retain the quality selected when they were added.

## Android download and install

1. Download **SpeedyWatch.apk** using the link below.
2. Open the downloaded file on your Android phone.
3. If Android asks, allow APK installation from your browser or file manager.
4. Confirm the installation.

### [Download SpeedyWatch.apk](https://github.com/demetre19/SpeedyWatch/releases/latest/download/SpeedyWatch.apk)

Current public build:

```text
Package: com.speedywatch.app
Version: 0.21
Version code: 21
Minimum Android version: Android 10 (API 29)
Supported device ABIs: arm64-v8a and armeabi-v7a
APK size: 106,391,266 bytes
SHA-256: 53564798ffd268f1d28e1cdcdebcffd3c2f788720ab562c1e0fb00babd7cfe10
Signing: Android debug signing key
```

This public v0.21 APK is debug-signed with APK Signature Scheme v2. It adds the Android multi-site browser for YouTube, Bilibili, Instagram, Vimeo, X, and Facebook, including selected-site search, strictly validated in-app media URLs, source-organized MP3/MP4 downloads, and available transcript support. Complete public MEGA folder and file links are also supported for playback through a dedicated URL-only picker entry. It fixes authenticated Vimeo downloads, preserves the selected MP4 resolution ceiling, accepts compatible AAC audio without requiring an M4A container, and shows visible progress while formats are checked. The public APK supports 64-bit and 32-bit ARM Android devices; x86_64 emulator/device builds are not included. A future switch to a production signing key may require uninstalling this build before installing the newly signed version.

## iPhone source and build

The native iPhone app is kept in the separate [`ios/` folder](https://github.com/demetre19/SpeedyWatch/tree/main/ios).

- [Download the repository source ZIP](https://github.com/demetre19/SpeedyWatch/archive/refs/heads/main.zip), then open `ios/SpeedyWatch.xcodeproj`.
- Or [browse the iPhone source and Xcode project](https://github.com/demetre19/SpeedyWatch/tree/main/ios) directly on GitHub.

The iPhone project requires Xcode 26 or newer. It can run in the iPhone Simulator immediately; installing it on a physical iPhone requires signing it with your own Apple Development team. A public signed iPhone build is not currently available.

## OpenRouter setup

Summaries, follow-up questions, and quizzes require your own OpenRouter API key.

1. Open **Settings** in SpeedyWatch.
2. Paste your OpenRouter API key.
3. Refresh the model list.
4. Choose a text model. SpeedyWatch prefers **Inception: Mercury 2** when it is available and shows each model's context length and advertised per-million-token input/output prices. Use the model picker filters to narrow the list to free or long-context options.
5. Edit the summary or quiz prompts if needed, then tap **Save**.

The API key is encrypted with Android Keystore AES-GCM on Android and stored in Keychain on iPhone. Settings masks the key by default and shows only a short prefix and suffix check.

## Using transcripts, summaries, and quizzes

1. Open a captioned supported video in SpeedyWatch. Android supports the listed media services; iPhone remains YouTube-only.
2. Tap the **Video Subs** icon, choose an available caption language or manual/auto-generated track, and load the transcript.
3. Switch between line and paragraph view, search or copy the transcript, optionally follow playback, or tap a timestamp to seek the video.
4. Choose **Summary One** or **Summary Two** to use its independently saved prompt.
   If you close the modal and choose the same summary again, SpeedyWatch reuses its private cached result when the generation context is unchanged.
   On Android, closing the modal after requesting a summary returns the video to the position captured when that Summary button was pressed.
5. After a summary succeeds, use **Continue with a question** beneath it to ask follow-up questions.
6. Tap **Save summary** to add the original generated summary to the local bookmark library, or **Share summary** to send it with the original video URL.
7. Tap the **Quiz** icon from the main toolbar to create a pre-watch question guide. **Save quiz** and **Share quiz** become available after the quiz succeeds.
8. Use the bookmark icon beside Settings to search saved summaries and quizzes, reopen their original videos, or share a saved item.

Transcript availability depends on the captions exposed by the selected service for that video.

## Privacy and network use

- SpeedyWatch does not add analytics or advertising SDKs.
- Android loads supported service pages and available captions over HTTPS, restricts main-frame navigation to explicit first-party hosts, and treats approved media CDN hosts as resource-only. The iPhone app loads YouTube pages and captions over HTTPS.
- Android media downloads are processed on the device and written to the public `Downloads/SpeedyWatch` folder. SpeedyWatch does not upload downloaded media to its own service.
- Optional SponsorBlock lookups go directly to `https://sponsor.ajay.app` over HTTPS. SpeedyWatch sends the recommended four-character SHA-256 prefix of the YouTube video ID rather than the full ID, then accepts only the matching video from the response.
- Your OpenRouter API key remains in platform-protected storage: Android Keystore-encrypted app storage or iPhone Keychain.
- Transcript text and any follow-up question you submit are sent to OpenRouter only when you request a summary, follow-up answer, or quiz.
- Saved summaries, saved quizzes, and their source URLs remain in app-private local storage until you delete them. Follow-up chat history is not saved.
- Exported backup files contain settings plus saved summaries and quizzes, but never the OpenRouter API key. Restoring a backup replaces those exported settings and saved items.
- Automatically cached summary results remain in app-private local storage and are removed when the app's data is cleared. Follow-up chat turns are not included in the reusable cache.
- Unsupported main-frame links open through the platform's external app handler; approved media CDN hosts cannot become browsable destinations.

## Build from source

Android requirements:

- JDK 17
- Android SDK 36
- Android build tools available through `ANDROID_HOME`

Build the debug APK:

```bash
./gradlew --no-daemon :app:assembleDebug
```

The APK is generated at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### Build and test the iPhone app

You need Xcode 26 or newer and an iPhone Simulator running iOS 17 or newer.

1. Open `ios/SpeedyWatch.xcodeproj`.
2. Select the **SpeedyWatch** scheme and an iPhone Simulator.
3. Run the app.

The iPhone target uses bundle identifier `com.speedywatch.ios` and has a minimum deployment target of iOS 17.

Command-line build:

```bash
xcodebuild -project ios/SpeedyWatch.xcodeproj \
  -scheme SpeedyWatch \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  build
```

Unit tests do not require an OpenRouter key. Live UI parity tests do:

1. Create the ignored file `ios/LocalSecrets.xcconfig`.
2. Add only this setting:

   ```text
   OPENROUTER_API_KEY = your-key-here
   ```

3. Run the complete suite:

   ```bash
   xcodebuild -project ios/SpeedyWatch.xcodeproj \
     -scheme SpeedyWatch \
     -sdk iphonesimulator \
     -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
     -derivedDataPath ios/DerivedData \
     -xcconfig ios/LocalSecrets.xcconfig \
     test
   ```

The shared scheme maps the build setting into the DEBUG test-process environment. It does not write the key to the app or test bundle, and `LocalSecrets.xcconfig` remains outside source control.

`xcodebuild` may print command-line build settings. Keep keyed command output and result bundles private, then remove keyed build artifacts with `rm -rf ios/DerivedData`.

## Open-source notices

The Android app bundles [yt-dlp](https://github.com/yt-dlp/yt-dlp) under the Unlicense and [youtubedl-android](https://github.com/yausername/youtubedl-android) under GNU GPLv3, including its FFmpeg-based media-processing package and transitive components under their respective licenses. The exact bundled yt-dlp release is `2026.07.04`; corresponding upstream source and license text are available from the linked projects. Review those licenses before redistributing a modified APK.

Uses [SponsorBlock](https://sponsor.ajay.app/) data under [CC BY-NC-SA 4.0](https://creativecommons.org/licenses/by-nc-sa/4.0/). SponsorBlock support is optional and read-only; SpeedyWatch does not submit or vote on segments.

## Important limitations

- Supported services can change their players, caption endpoints, delivery interfaces, and page structures without notice.
- Android downloads depend on a video's public availability and each supported service's current delivery interfaces. Private, deleted, age- or region-restricted, DRM-protected, or otherwise inaccessible media may not download.
- Download only media you own or have permission and legal authority to save, and follow the selected service's terms and applicable copyright law.
- Built-in YouTube ad skipping is always enabled only while YouTube is active and remains best effort. Optional SponsorBlock community-segment skipping is also YouTube-only, defaults off, and depends on third-party submissions and API availability. Neither feature is a network-level ad blocker.
- Videos without accessible captions cannot use transcript, summary, or quiz features.
- OpenRouter usage may incur charges depending on the selected model and account.

## Project status

SpeedyWatch is an independent project and is not affiliated with or endorsed by YouTube, Google, Bilibili, Instagram, Meta, Vimeo, X, MEGA, OpenRouter, or Inception Labs.

Brought to you by the team from [SEO Time Machines](https://seotimemachines.com)
