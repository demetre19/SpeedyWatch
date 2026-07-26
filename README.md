<p align="center">
  <img src="logo.png" width="240" height="240" alt="SpeedyWatch logo">
</p>

<h1 align="center">SpeedyWatch</h1>

<p align="center">
  <strong>Watch more in less time.</strong><br>
  Control YouTube playback, search transcripts, create and save readable summaries, and prepare with focused pre-watch questions.
</p>

## Download SpeedyWatch

| Android | iPhone |
| --- | --- |
| **Android 10 and newer** | **iOS 17 and newer** |
| [**Download the installable Android APK**](https://github.com/demetre19/SpeedyWatch/releases/latest/download/SpeedyWatch.apk) | [**Download the source ZIP**](https://github.com/demetre19/SpeedyWatch/archive/refs/heads/main.zip) |
| Current public APK: **v0.17**, debug-signed | Open the separate [`ios/` iPhone project](https://github.com/demetre19/SpeedyWatch/tree/main/ios) in Xcode |

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

SpeedyWatch is a focused Android and iPhone YouTube browser for people who want faster playback and useful transcript tools without leaving the video.

- Use the leftmost **Search** action to enter YouTube keywords or paste a supported YouTube video URL. Keywords open YouTube results; a URL opens that exact video for playback, captions, summaries, quizzes, or downloading.
- Open shared YouTube links directly in SpeedyWatch from the Android or iPhone share sheet.
- Set playback speed from **0.25x to 4x** with common presets, direct decimal entry, or **0.1x** adjustments.
- Choose a **Normal**, **Careful**, **Lecture**, or **Podcast** profile, and optionally add 0.5x only during caption gaps with adaptive speed.
- Keep the chosen baseline speed when YouTube replaces or resets its video player; adaptive boosts never replace that saved baseline.
- Choose and persist a custom **default playback speed** in Settings for future app launches.
- Skip known YouTube ads and feed-ad elements on a best-effort basis.
- Optionally skip community-submitted sponsor, self-promotion, and interaction segments from SponsorBlock, with a brief notice and Undo action after each skip.
- On Android, tap Download to use a valid YouTube video URL copied to the clipboard—including standard, short, mobile, music, Shorts, Live, embed, no-cookie, and attribution links—or fall back to the current video. Choose MP3 audio or an MP4 up to an available resolution for that exact video. Downloads continue in the background, use the YouTube video title as the filename, and are written to `Downloads/SpeedyWatch`.
- Choose among available caption languages and manual or auto-generated tracks, search in line or paragraph view, copy the transcript, and optionally follow the current playback position.
- Tap any transcript line or paragraph to jump to that moment and return to the video.
- Create two independently configurable summaries through OpenRouter, then ask follow-up questions in the same transcript view. Android renders each `You` turn in a padded, rounded dark-red bubble so it remains visually distinct from AI output.
- Successful Summary One and Summary Two results are cached automatically in app-private storage. Pressing the same Summary button again immediately renders the cached result without another OpenRouter request when the summary type, prompt, model, source URL, and transcript are unchanged.
- Save summaries and generated quiz guides locally with their original YouTube URL, then search titles, types, headings, and body text from the bookmark library.
- Share a generated summary, generated quiz, or saved item through the platform's native share surface on Android and iPhone. Every share includes the original YouTube URL.
- Export settings plus saved summaries and quizzes to a cross-platform JSON backup, then restore it on Android or iPhone. OpenRouter API keys are never included.
- Select **6, 10, 12, or 20** as request context for the editable Quiz prompt.
- Edit the Summary One, Summary Two, and Quiz prompts in Settings. These fields are the only source of AI output instructions. The live model picker shows context length and per-million-token input/output prices and can filter free or long-context models.
- On Android, check the official latest stable GitHub Release from Settings, see an explicit up-to-date status when no newer version exists, and manually download its APK to the Downloads folder.

## Android download and install

1. Download **SpeedyWatch.apk** using the link below.
2. Open the downloaded file on your Android phone.
3. If Android asks, allow APK installation from your browser or file manager.
4. Confirm the installation.

### [Download SpeedyWatch.apk](https://github.com/demetre19/SpeedyWatch/releases/latest/download/SpeedyWatch.apk)

Current public build:

```text
Package: com.speedywatch.app
Version: 0.17
Version code: 17
Minimum Android version: Android 10 (API 29)
Supported device ABIs: arm64-v8a and armeabi-v7a
APK size: 105,978,650 bytes
SHA-256: cefc257dbe2ace44e943770235635985d20487c7666b5f9fe6414864bce0fbee
Signing: Android debug signing key
```

This public v0.17 APK is debug-signed with APK Signature Scheme v2. It adds Android share-to-SpeedyWatch entry, the expanded transcript reader, JSON backup and restore, OpenRouter model cost/context guidance, playback profiles with adaptive caption-gap speed, and optional privacy-preserving SponsorBlock segment skipping. The public APK supports 64-bit and 32-bit ARM Android devices; x86_64 emulator/device builds are not included. A future switch to a production signing key may require uninstalling this build before installing the newly signed version.

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

1. Open a captioned YouTube video in SpeedyWatch.
2. Tap the **YouTube Subs** icon, choose an available caption language or manual/auto-generated track, and load the transcript.
3. Switch between line and paragraph view, search or copy the transcript, optionally follow playback, or tap a timestamp to seek the video.
4. Choose **Summary One** or **Summary Two** to use its independently saved prompt.
   If you close the modal and choose the same summary again, SpeedyWatch reuses its private cached result when the generation context is unchanged.
5. After a summary succeeds, use **Continue with a question** beneath it to ask follow-up questions.
6. Tap **Save summary** to add the original generated summary to the local bookmark library, or **Share summary** to send it with the original video URL.
7. Tap the **Quiz** icon from the main toolbar to create a pre-watch question guide. **Save quiz** and **Share quiz** become available after the quiz succeeds.
8. Use the bookmark icon beside Settings to search saved summaries and quizzes, reopen their original videos, or share a saved item.

Transcript availability depends on the captions exposed by YouTube for the selected video.

## Privacy and network use

- SpeedyWatch does not add analytics or advertising SDKs.
- YouTube pages and captions are loaded from YouTube over HTTPS.
- Android media downloads are processed on the device and written to the public `Downloads/SpeedyWatch` folder. SpeedyWatch does not upload downloaded media to its own service.
- Optional SponsorBlock lookups go directly to `https://sponsor.ajay.app` over HTTPS. SpeedyWatch sends the recommended four-character SHA-256 prefix of the YouTube video ID rather than the full ID, then accepts only the matching video from the response.
- Your OpenRouter API key remains in platform-protected storage: Android Keystore-encrypted app storage or iPhone Keychain.
- Transcript text and any follow-up question you submit are sent to OpenRouter only when you request a summary, follow-up answer, or quiz.
- Saved summaries, saved quizzes, and their source URLs remain in app-private local storage until you delete them. Follow-up chat history is not saved.
- Exported backup files contain settings plus saved summaries and quizzes, but never the OpenRouter API key. Restoring a backup replaces those exported settings and saved items.
- Automatically cached summary results remain in app-private local storage and are removed when the app's data is cleared. Follow-up chat turns are not included in the reusable cache.
- Links outside YouTube open through the platform's external app handler.

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

- YouTube can change its player, caption endpoints, and page structure without notice.
- Android downloads depend on a video's public availability and YouTube's current delivery interfaces. Private, deleted, age- or region-restricted, DRM-protected, or otherwise inaccessible media may not download.
- Download only media you own or have permission and legal authority to save, and follow YouTube's terms and applicable copyright law.
- Built-in YouTube ad skipping is always enabled and remains best effort. Optional SponsorBlock community-segment skipping is separate, defaults off, and depends on third-party submissions and API availability. Neither feature is a network-level ad blocker.
- Videos without accessible captions cannot use transcript, summary, or quiz features.
- OpenRouter usage may incur charges depending on the selected model and account.

## Project status

SpeedyWatch is an independent project and is not affiliated with or endorsed by YouTube, Google, OpenRouter, or Inception Labs.

Brought to you by the team from [SEO Time Machines](https://seotimemachines.com)
