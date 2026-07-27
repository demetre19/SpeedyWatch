# Changelog

## v0.18 — 2026-07-27

### Added

- Queue up to 50 additional Android MP3 or MP4 downloads and process them one at a time in first-in, first-out order.
- Show the active format, progress, estimated time, and waiting-download count in the foreground notification.
- Cancel only the active download from its notification while preserving the remaining queue.

### Fixed

- Stop completed downloads from appearing stuck at 100% by showing a separate finishing state, avoiding unnecessary full MP4 recoding, and timing out stalled finalization so the queue can continue.
- Keep completed-download notifications distinct across download-service restarts.

## v0.17 — 2026-07-26

### Added

- Open supported YouTube links shared to SpeedyWatch through Android's share sheet or HTTPS app links.
- Choose manual or auto-generated caption tracks, switch between line and paragraph transcript views, follow playback, and copy the transcript.
- Export and restore a versioned cross-platform JSON backup containing non-secret settings plus saved summaries and quizzes. OpenRouter API keys, cached summaries, and chat history are excluded.
- See OpenRouter model context length and advertised per-million-token input/output prices, with Free and Long context filters.
- Use Normal, Careful, Lecture, and Podcast playback profiles, with an optional 0.5x caption-gap adaptive boost that returns to the selected baseline.
- Optionally skip SponsorBlock sponsor, self-promotion, and interaction segments using the privacy-preserving four-character video-ID hash prefix endpoint, with a temporary Undo action.

### Changed

- Playback status now identifies when adaptive speed is enabled.
- SponsorBlock segment data refreshes when YouTube changes videos through in-page navigation.
- README and project contracts now document the new features, backup privacy boundaries, and SponsorBlock attribution.

### iPhone source parity

- Added source-level equivalents for sharing, transcript reading, backup, model guidance, playback profiles, adaptive speed, and SponsorBlock. Public iPhone delivery remains source-only and requires developer signing.
