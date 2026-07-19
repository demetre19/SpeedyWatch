<p align="center">
  <img src="logo.png" width="240" height="240" alt="SpeedyWatch logo">
</p>

<h1 align="center">SpeedyWatch</h1>

<p align="center">
  <strong>Watch more in less time.</strong><br>
  Control YouTube playback, search transcripts, create and save readable summaries, and prepare with focused pre-watch questions.
</p>

<p align="center">
  <strong>Android 10 and newer</strong>
</p>

<p align="center">
  <a href="https://github.com/demetre19/SpeedyWatch/releases/latest/download/SpeedyWatch.apk"><strong>Download the latest SpeedyWatch APK</strong></a>
</p>

<p align="center">
  <a href="https://github.com/demetre19/SpeedyWatch/releases/latest">Release notes and previous downloads</a>
</p>

---

## What SpeedyWatch does

SpeedyWatch is a focused Android YouTube browser for people who want faster playback and useful transcript tools without leaving the video.

- Set playback speed from **0.25x to 4x**.
- Use common presets, direct decimal entry, or **0.1x** adjustments.
- Keep the selected speed when YouTube replaces or resets its video player.
- Skip known YouTube ads and feed-ad elements on a best-effort basis.
- Load and search captions for the current video.
- Tap any transcript line to jump to that moment and return to the video.
- Create two independently configurable summaries through OpenRouter.
- Save summaries locally with their original YouTube URL, then search titles, headings, and body text from the bookmark library.
- Select **6, 10, 12, or 20** as request context for the editable Quiz prompt.
- Edit the Summary One, Summary Two, and Quiz prompts in Settings. These fields are the only source of AI output instructions.

## Download and install

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

This public APK is debug-signed. It is installable, but a future switch to a production signing key may require uninstalling this build before installing the newly signed version.

## OpenRouter setup

Summaries and quizzes require your own OpenRouter API key.

1. Open **Settings** in SpeedyWatch.
2. Paste your OpenRouter API key.
3. Refresh the model list.
4. Choose a text model. SpeedyWatch prefers **Inception: Mercury 2** when it is available.
5. Edit the summary or quiz prompts if needed, then tap **Save**.

The API key is encrypted with Android Keystore AES-GCM before it is stored. The Settings screen masks the key by default and shows only a short prefix and suffix check.

## Using transcripts, summaries, and quizzes

1. Open a captioned YouTube video in SpeedyWatch.
2. Tap the **YouTube Subs** icon to load the transcript.
3. Search the transcript or tap a timestamp to seek the video.
4. Choose **Summary One** to use its saved prompt.
5. Choose **Summary Two** to use its independently saved prompt.
6. Tap **Save summary** to add a generated summary to the local bookmark library.
7. Use the bookmark icon beside Settings to search saved summaries or reopen their original videos.
8. Tap the **Quiz** icon from the main toolbar to create a pre-watch question guide.

Transcript availability depends on the captions exposed by YouTube for the selected video.

## Privacy and network use

- SpeedyWatch does not add analytics or advertising SDKs.
- YouTube pages and captions are loaded from YouTube over HTTPS.
- Your OpenRouter API key remains encrypted in Android app storage.
- Transcript text is sent to OpenRouter only when you request a summary or quiz.
- Saved summaries and their source URLs remain in the app-private local database until you delete them.
- Links outside YouTube open through Android's external app handler.

## Build from source

Requirements:

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

## Important limitations

- YouTube can change its player, caption endpoints, and page structure without notice.
- Ad skipping is best effort and is not a network-level ad blocker.
- Videos without accessible captions cannot use transcript, summary, or quiz features.
- OpenRouter usage may incur charges depending on the selected model and account.

## Project status

SpeedyWatch is an independent project and is not affiliated with or endorsed by YouTube, Google, OpenRouter, or Inception Labs.

Brought to you by the team from [SEO Time Machines](https://seotimemachines.com)
