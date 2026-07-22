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
| Current public build: **v0.4**, debug-signed | Open the separate [`ios/` iPhone project](https://github.com/demetre19/SpeedyWatch/tree/main/ios) in Xcode |

> **Android release note:** the public v0.4 APK is the currently verified download. It predates the newest source changes for summary follow-up chat and quiz bookmarking; those Android changes will require a rebuilt release after the configured JDK 17 and Android SDK are available.

> **iPhone availability:** the iPhone app is currently provided as source code for an Xcode build. There is no Apple-signed IPA, TestFlight, or App Store download yet.

[Release notes and previous Android downloads](https://github.com/demetre19/SpeedyWatch/releases/latest)

---

## What SpeedyWatch does

SpeedyWatch is a focused Android and iPhone YouTube browser for people who want faster playback and useful transcript tools without leaving the video.

- Set playback speed from **0.25x to 4x**.
- Use common presets, direct decimal entry, or **0.1x** adjustments.
- Keep the selected speed when YouTube replaces or resets its video player.
- Choose and persist a custom **default playback speed** in Settings for future app launches.
- Skip known YouTube ads and feed-ad elements on a best-effort basis.
- Load and search captions for the current video.
- Tap any transcript line to jump to that moment and return to the video.
- Create two independently configurable summaries through OpenRouter, then ask follow-up questions in the same transcript view.
- Save summaries and generated quiz guides locally with their original YouTube URL, then search titles, types, headings, and body text from the bookmark library.
- Select **6, 10, 12, or 20** as request context for the editable Quiz prompt.
- Edit the Summary One, Summary Two, and Quiz prompts in Settings. These fields are the only source of AI output instructions.

## Android download and install

1. Download **SpeedyWatch.apk** using the link below.
2. Open the downloaded file on your Android phone.
3. If Android asks, allow APK installation from your browser or file manager.
4. Confirm the installation.

### [Download SpeedyWatch.apk](https://github.com/demetre19/SpeedyWatch/releases/latest/download/SpeedyWatch.apk)

Current build:

```text
Package: com.speedywatch.app
Version: 0.4
Version code: 4
Minimum Android version: Android 10 (API 29)
SHA-256: e49a7e6baa064b802635a853ee8b246438766cdf4dc450abf10ae3eca7365582
Signing: Android debug signing key
```

This public APK is debug-signed. It is installable, but a future switch to a production signing key may require uninstalling this build before installing the newly signed version. The v0.4 APK predates the newest source changes for summary follow-up chat and quiz bookmarking.

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
4. Choose a text model. SpeedyWatch prefers **Inception: Mercury 2** when it is available.
5. Edit the summary or quiz prompts if needed, then tap **Save**.

The API key is encrypted with Android Keystore AES-GCM on Android and stored in Keychain on iPhone. Settings masks the key by default and shows only a short prefix and suffix check.

## Using transcripts, summaries, and quizzes

1. Open a captioned YouTube video in SpeedyWatch.
2. Tap the **YouTube Subs** icon to load the transcript.
3. Search the transcript or tap a timestamp to seek the video.
4. Choose **Summary One** or **Summary Two** to use its independently saved prompt.
5. Ask follow-up questions using the field beneath the generated summary.
6. Tap **Save summary** to add the original generated summary to the local bookmark library.
7. Tap the **Quiz** icon from the main toolbar to create a pre-watch question guide, then tap **Save quiz** to bookmark it.
8. Use the bookmark icon beside Settings to search saved summaries and quizzes or reopen their original videos.

Transcript availability depends on the captions exposed by YouTube for the selected video.

## Privacy and network use

- SpeedyWatch does not add analytics or advertising SDKs.
- YouTube pages and captions are loaded from YouTube over HTTPS.
- Your OpenRouter API key remains in platform-protected storage: Android Keystore-encrypted app storage or iPhone Keychain.
- Transcript text and any follow-up question you submit are sent to OpenRouter only when you request a summary, follow-up answer, or quiz.
- Saved summaries, saved quizzes, and their source URLs remain in app-private local storage until you delete them. Follow-up chat history is not saved.
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

## Important limitations

- YouTube can change its player, caption endpoints, and page structure without notice.
- Ad skipping is best effort and is not a network-level ad blocker.
- Videos without accessible captions cannot use transcript, summary, or quiz features.
- OpenRouter usage may incur charges depending on the selected model and account.

## Project status

SpeedyWatch is an independent project and is not affiliated with or endorsed by YouTube, Google, OpenRouter, or Inception Labs.

Brought to you by the team from [SEO Time Machines](https://seotimemachines.com)
