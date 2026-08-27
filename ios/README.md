# iOS development client

The `ios/` folder contains the SwiftUI iPhone client. It supports the existing
YouTube workflow and an in-app X.com browser selected from the top navigation.

While X.com is active, SpeedyWatch applies best-effort sponsored-post filtering:

- narrowly scoped WebKit resource rules target known X advertising hosts;
- a document-end filter removes known placement markers and sponsored labels;
- a `MutationObserver` repeats the filter as the timeline loads more posts.

X serves sponsored posts through markup and normal timeline responses that can
change without notice. Filtering is therefore not guaranteed, and the client
does not bypass login, subscriptions, paywalls, or platform security controls.
Non-X HTTPS destinations opened from X remain external. YouTube navigation and
its existing ad-skipping and SponsorBlock behavior are unchanged.

## Build for the iOS Simulator

```sh
xcodebuild -project ios/SpeedyWatch.xcodeproj \
  -scheme SpeedyWatch \
  -sdk iphonesimulator \
  -destination 'platform=iOS Simulator,name=iPhone 17 Pro' \
  CODE_SIGNING_ALLOWED=NO build
```

The iOS client is a development build and is not currently distributed as an
installable public download.
