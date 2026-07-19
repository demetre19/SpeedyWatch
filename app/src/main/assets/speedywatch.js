(() => {
    "use strict";

    const existing = window.__speedyWatchController;
    if (existing) {
        return "reused";
    }

    const state = {
        speed: 1,
        adSkipping: true,
        timer: 0,
        pending: false
    };

    const mediaElements = () => Array.from(document.querySelectorAll("video, audio"));

    const playerElement = () => document.getElementById("movie_player");

    const youtubePlayer = () => {
        try {
            const host = document.getElementById("ytd-player");
            if (host && typeof host.getPlayer === "function") {
                return host.getPlayer();
            }
            const player = playerElement();
            return player && typeof player.getPlayerState === "function" ? player : null;
        } catch (_) {
            return null;
        }
    };

    const isAdShowing = () => {
        const player = playerElement();
        if (player && (player.classList.contains("ad-showing") || player.classList.contains("ad-interrupting"))) {
            return true;
        }
        return Boolean(document.querySelector(
            ".ytp-ad-player-overlay, .ytp-ad-text, .ytp-ad-preview-container, " +
            ".ytp-skip-ad-button, .ytp-ad-skip-button, .ytp-ad-skip-button-modern"
        ));
    };

    const removeFeedAds = () => {
        if (!state.adSkipping) {
            return;
        }
        document.querySelectorAll(
            "ytd-ad-slot-renderer, ytd-display-ad-renderer, " +
            "ytd-promoted-sparkles-web-renderer, ytd-promoted-video-renderer, " +
            "ytd-in-feed-ad-layout-renderer"
        ).forEach((node) => node.remove());
    };

    const clickSkipButton = () => {
        const button = document.querySelector(
            ".ytp-skip-ad-button, .ytp-ad-skip-button, .ytp-ad-skip-button-modern"
        );
        if (!button) {
            return false;
        }
        button.click();
        return true;
    };

    const skipVideoAd = () => {
        if (!state.adSkipping || !isAdShowing()) {
            return false;
        }

        if (clickSkipButton()) {
            return true;
        }

        const player = youtubePlayer();
        try {
            if (player && typeof player.skipAd === "function") {
                player.skipAd();
                return true;
            }
        } catch (_) {
            // Fall through to the media-element strategy.
        }

        const video = document.querySelector("video");
        try {
            if (video && Number.isFinite(video.duration) && video.duration > 0) {
                video.currentTime = video.duration;
                return true;
            }
        } catch (_) {
            // Fall through to the player restart strategy.
        }

        try {
            if (player && typeof player.cancelPlayback === "function") {
                player.cancelPlayback();
                window.setTimeout(() => {
                    try {
                        if (typeof player.playVideo === "function") {
                            player.playVideo();
                        }
                    } catch (_) {
                        // A later controller tick will recover playback state.
                    }
                }, 300);
                return true;
            }
        } catch (_) {
            // YouTube private APIs change regularly; the next tick retries safely.
        }
        return false;
    };

    const applySpeed = () => {
        if (state.adSkipping && isAdShowing()) {
            return;
        }
        mediaElements().forEach((media) => {
            try {
                media.defaultPlaybackRate = state.speed;
                if (media.playbackRate !== state.speed) {
                    media.playbackRate = state.speed;
                }
                if ("preservesPitch" in media) {
                    media.preservesPitch = true;
                }
            } catch (_) {
                // Detached media nodes are harmless; a later tick handles replacements.
            }
        });
    };

    const tick = () => {
        state.pending = false;
        removeFeedAds();
        if (!skipVideoAd()) {
            applySpeed();
        }
    };

    const scheduleTick = () => {
        if (state.pending) {
            return;
        }
        state.pending = true;
        window.setTimeout(tick, 80);
    };

    const api = {
        version: 2,
        setSpeed(value) {
            const parsed = Number(value);
            if (!Number.isFinite(parsed)) {
                return state.speed;
            }
            state.speed = Math.min(4, Math.max(0.25, parsed));
            tick();
            return state.speed;
        },
        setAdSkipping(enabled) {
            state.adSkipping = Boolean(enabled);
            tick();
            return state.adSkipping;
        },
        getCaptionTrack() {
            try {
                const player = playerElement();
                const response = player && typeof player.getPlayerResponse === "function"
                    ? player.getPlayerResponse()
                    : window.ytInitialPlayerResponse;
                const renderer = response
                    && response.captions
                    && response.captions.playerCaptionsTracklistRenderer;
                const tracks = renderer && renderer.captionTracks;
                const heading = document.querySelector(
                    "h1.ytd-watch-metadata yt-formatted-string, h1 yt-formatted-string, h1"
                );
                const title = heading && heading.textContent.trim()
                    ? heading.textContent.trim()
                    : document.title.replace(/ - YouTube$/, "");
                if (!Array.isArray(tracks) || tracks.length === 0) {
                    return JSON.stringify({ error: "missing", title });
                }
                const track = tracks.find((item) => item.kind !== "asr") || tracks[0];
                return JSON.stringify({
                    baseUrl: track.baseUrl || "",
                    languageCode: track.languageCode || "",
                    title
                });
            } catch (_) {
                return JSON.stringify({ error: "unavailable" });
            }
        },
        requestCaptions() {
            const button = document.querySelector("button.ytp-subtitles-button.ytp-button");
            if (!button) {
                return "missing";
            }
            button.click();
            window.setTimeout(() => button.click(), 150);
            return "triggered";
        },
        seekTo(value) {
            const parsed = Number(value);
            const video = document.querySelector("video");
            if (!video || !Number.isFinite(parsed)) {
                return false;
            }
            video.currentTime = Math.min(604800, Math.max(0, parsed));
            return true;
        },
        status() {
            return { speed: state.speed, adSkipping: state.adSkipping, adShowing: isAdShowing() };
        }
    };

    window.__speedyWatchController = api;

    document.addEventListener("playing", scheduleTick, true);
    document.addEventListener("loadeddata", scheduleTick, true);
    document.addEventListener("ratechange", scheduleTick, true);
    document.addEventListener("yt-navigate-finish", scheduleTick, true);

    const observer = new MutationObserver(scheduleTick);
    observer.observe(document.documentElement, {
        attributes: true,
        attributeFilter: ["class"],
        childList: true,
        subtree: true
    });

    state.timer = window.setInterval(tick, 500);
    tick();
    return "installed";
})();
