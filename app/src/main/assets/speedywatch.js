(() => {
    "use strict";

    const existing = window.__speedyWatchController;
    if (existing) {
        return "reused";
    }

    const state = {
        speed: 1,
        adSkipping: true,
        adaptiveSpeed: false,
        adaptiveBoost: 0.5,
        captionSeen: false,
        lastCaptionAt: 0,
        sponsorSkipping: false,
        sponsorSegments: [],
        ignoredSponsorSegment: null,
        sponsorNoticeTimer: 0,
        timer: 0,
        pending: false,
        adProcessing: false,
        pictureInPictureActive: false,
        pictureInPicturePlaybackRequested: false
    };
    const documentHidden = Object.getOwnPropertyDescriptor(Document.prototype, "hidden");
    const documentVisibilityState =
        Object.getOwnPropertyDescriptor(Document.prototype, "visibilityState");
    const currentDocumentHidden = () => documentHidden?.get
        ? documentHidden.get.call(document) : false;
    const currentDocumentVisibilityState = () => documentVisibilityState?.get
        ? documentVisibilityState.get.call(document) : "visible";
    try {
        Object.defineProperty(document, "hidden", {
            configurable: true,
            get: () => state.pictureInPictureActive
                ? false : currentDocumentHidden()
        });
        Object.defineProperty(document, "visibilityState", {
            configurable: true,
            get: () => state.pictureInPictureActive
                ? "visible" : currentDocumentVisibilityState()
        });
    } catch (_) {
        // Continue without the visibility override if the page locks these properties.
    }
    window.addEventListener("visibilitychange", (event) => {
        if (state.pictureInPictureActive) {
            event.stopImmediatePropagation();
        }
    }, true);
    const mediaPause = HTMLMediaElement.prototype.pause;
    HTMLMediaElement.prototype.pause = function() {
        if (state.pictureInPictureActive && state.pictureInPicturePlaybackRequested) {
            return;
        }
        return mediaPause.call(this);
    };

    const mediaElements = () => Array.from(document.querySelectorAll("video, audio"));
    const soundCloudPlaybackButton = () => {
        if (!/(^|\.)soundcloud\.com$/i.test(window.location.hostname)) {
            return null;
        }
        return document.querySelector(
            ".playControl.playing, .playControls__play.playing, " +
            "button[title^='Pause'], button[aria-label^='Pause'], " +
            ".playControl, .playControls__play, " +
            "button[title^='Play'], button[aria-label^='Play']"
        );
    };

    const soundCloudPlaying = () => {
        const button = soundCloudPlaybackButton();
        if (!button) {
            return false;
        }
        const label = `${button.getAttribute("title") || ""} ${button.getAttribute("aria-label") || ""}`;
        return button.classList.contains("playing") || /\bpause\b/i.test(label);
    };

    const activePictureInPictureMedia = () => mediaElements().find((element) =>
        !element.paused
        && !element.ended
        && element.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA
    ) || null;
    const pictureInPictureLabel = () => {
        const host = window.location.hostname.toLowerCase();
        if (host === "youtu.be" || host === "youtube.com" || host.endsWith(".youtube.com")) {
            return "YouTube";
        }
        if (host === "bilibili.com" || host.endsWith(".bilibili.com")) {
            return "Bilibili";
        }
        if (host === "instagram.com" || host.endsWith(".instagram.com")) {
            return "Instagram";
        }
        if (host === "vimeo.com" || host.endsWith(".vimeo.com")) {
            return "Vimeo";
        }
        if (host === "x.com" || host.endsWith(".x.com")
                || host === "twitter.com" || host.endsWith(".twitter.com")) {
            return "X";
        }
        if (host === "facebook.com" || host.endsWith(".facebook.com")
                || host === "fb.watch" || host.endsWith(".fb.watch")) {
            return "Facebook";
        }
        if (host === "soundcloud.com" || host.endsWith(".soundcloud.com")) {
            return "SoundCloud";
        }
        return "SpeedyWatch";
    };



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
        ).forEach((node) => {
            const parent = typeof node.closest === "function"
                ? node.closest("ytd-rich-item-renderer")
                : null;
            (parent || node).remove();
        });
    };

    const clickSkipButton = () => {
        const button = document.querySelector(
            ".ytp-skip-ad-button, .ytp-ad-skip-button, .ytp-ad-skip-button-modern"
        );
        if (!button || button.offsetParent === null) {
            return false;
        }
        button.click();
        return true;
    };

    const skipVideoAd = () => {
        if (!state.adSkipping || state.adProcessing || !isAdShowing()) {
            return false;
        }
        state.adProcessing = true;

        try {
            const player = youtubePlayer();

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
                // Fall through to the visible skip-button strategy.
            }

            if (clickSkipButton()) {
                return true;
            }

            const video = document.querySelector("video");
            try {
                if (video && Number.isFinite(video.duration) && video.duration > 0) {
                    video.currentTime = video.duration;
                    return true;
                }
            } catch (_) {
                // Fall through to the player skip API.
            }

            try {
                if (player && typeof player.skipAd === "function") {
                    player.skipAd();
                    return true;
                }
            } catch (_) {
                // YouTube private APIs change regularly; the next tick retries safely.
            }
            return false;
        } finally {
            window.setTimeout(() => {
                state.adProcessing = false;
            }, 800);
        }
    };

    const showSponsorNotice = (segment, video) => {
        const previous = document.getElementById("speedywatch-sponsor-notice");
        if (previous) {
            previous.remove();
        }
        const labels = ["Sponsor", "Self-promotion", "Interaction"];
        const notice = document.createElement("div");
        notice.id = "speedywatch-sponsor-notice";
        notice.style.cssText = "position:fixed;right:12px;bottom:84px;z-index:2147483647;" +
            "display:flex;align-items:center;gap:10px;padding:10px 12px;border-radius:8px;" +
            "background:#1e1e1e;color:#fff;font:600 13px system-ui;box-shadow:0 4px 18px #0009";
        const label = document.createElement("span");
        label.textContent = (labels[segment.category] || "Community") + " segment skipped";
        const undo = document.createElement("button");
        undo.type = "button";
        undo.textContent = "Undo";
        undo.style.cssText = "min-width:44px;min-height:36px;border:1px solid #ff0033;" +
            "border-radius:6px;background:#303030;color:#fff;font:inherit";
        undo.addEventListener("click", () => {
            state.ignoredSponsorSegment = segment;
            video.currentTime = segment.start;
            notice.remove();
        });
        notice.append(label, undo);
        document.body.appendChild(notice);
        window.clearTimeout(state.sponsorNoticeTimer);
        state.sponsorNoticeTimer = window.setTimeout(() => notice.remove(), 5000);
    };

    const skipSponsorSegment = () => {
        if (!state.sponsorSkipping || state.adProcessing || isAdShowing()) {
            return false;
        }
        const video = document.querySelector("video");
        if (!video || video.paused || !Number.isFinite(video.currentTime)) {
            return false;
        }
        if (state.ignoredSponsorSegment) {
            const ignored = state.ignoredSponsorSegment;
            if (video.currentTime >= ignored.start - 0.25 && video.currentTime < ignored.end) {
                return false;
            }
            state.ignoredSponsorSegment = null;
        }
        const segment = state.sponsorSegments.find((item) =>
            video.currentTime >= item.start && video.currentTime < item.end - 0.05
        );
        if (!segment) {
            return false;
        }
        video.currentTime = segment.end;
        showSponsorNotice(segment, video);
        return true;
    };

    const effectiveSpeed = (media) => {
        if (!state.adaptiveSpeed || media.paused) {
            return state.speed;
        }
        const captionVisible = Array.from(document.querySelectorAll(
            ".ytp-caption-segment, .caption-window .captions-text"
        )).some((node) => node.textContent && node.textContent.trim());
        if (captionVisible) {
            state.captionSeen = true;
            state.lastCaptionAt = Date.now();
            return state.speed;
        }
        const inCaptionGap = state.captionSeen && Date.now() - state.lastCaptionAt >= 1200;
        return inCaptionGap ? Math.min(4, state.speed + state.adaptiveBoost) : state.speed;
    };

    const applySpeed = () => {
        if (state.adSkipping && isAdShowing()) {
            return;
        }
        mediaElements().forEach((media) => {
            try {
                const target = effectiveSpeed(media);
                media.defaultPlaybackRate = state.speed;
                if (media.playbackRate !== target) {
                    media.playbackRate = target;
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
            skipSponsorSegment();
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
        version: 15,
        setSpeed(value) {
            const parsed = Number(value);
            if (!Number.isFinite(parsed)) {
                return state.speed;
            }
            state.speed = Math.min(4, Math.max(0.25, parsed));
            tick();
            return state.speed;
        },
        setAdaptiveSpeed(enabled, boost) {
            const parsedBoost = Number(boost);
            state.adaptiveSpeed = Boolean(enabled);
            state.adaptiveBoost = Number.isFinite(parsedBoost)
                ? Math.min(1.5, Math.max(0.1, parsedBoost))
                : 0.5;
            if (!state.adaptiveSpeed) {
                state.captionSeen = false;
                state.lastCaptionAt = 0;
            }
            tick();
            return state.adaptiveSpeed;
        },
        setSponsorSkipping(enabled) {
            state.sponsorSkipping = Boolean(enabled);
            if (!state.sponsorSkipping) {
                state.sponsorSegments = [];
                state.ignoredSponsorSegment = null;
            }
            tick();
            return state.sponsorSkipping;
        },
        clearSponsorSegments() {
            state.sponsorSegments = [];
            state.ignoredSponsorSegment = null;
            return true;
        },
        addSponsorSegment(start, end, category) {
            const parsedStart = Number(start);
            const parsedEnd = Number(end);
            const parsedCategory = Number(category);
            if (!Number.isFinite(parsedStart) || !Number.isFinite(parsedEnd)
                    || !Number.isInteger(parsedCategory)
                    || parsedStart < 0 || parsedEnd <= parsedStart || parsedEnd > 604800
                    || parsedCategory < 0 || parsedCategory > 2
                    || state.sponsorSegments.length >= 500) {
                return false;
            }
            state.sponsorSegments.push({
                start: parsedStart,
                end: parsedEnd,
                category: parsedCategory
            });
            state.sponsorSegments.sort((left, right) => left.start - right.start);
            return true;
        },
        setAdSkipping() {
            state.adSkipping = true;
            tick();
            return true;
        },
        getCaptionTrack() {
            try {
                const player = playerElement();
                const currentVideoId = new URL(window.location.href).searchParams.get("v") || "";
                const responses = [];
                if (player && typeof player.getPlayerResponse === "function") {
                    responses.push(player.getPlayerResponse());
                }
                if (window.ytInitialPlayerResponse) {
                    responses.push(window.ytInitialPlayerResponse);
                }
                const configuredResponse = window.ytplayer
                    && window.ytplayer.config
                    && window.ytplayer.config.args
                    && window.ytplayer.config.args.player_response;
                if (configuredResponse) {
                    try {
                        responses.push(typeof configuredResponse === "string"
                            ? JSON.parse(configuredResponse) : configuredResponse);
                    } catch (_) {
                        // Keep current player responses when a legacy config value is malformed.
                    }
                }
                const response = responses.find((candidate) => {
                    const candidateVideoId = candidate
                        && candidate.videoDetails
                        && candidate.videoDetails.videoId;
                    const renderer = candidate
                        && candidate.captions
                        && candidate.captions.playerCaptionsTracklistRenderer;
                    return (!currentVideoId || !candidateVideoId || candidateVideoId === currentVideoId)
                        && renderer
                        && Array.isArray(renderer.captionTracks)
                        && renderer.captionTracks.length > 0;
                });
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
                const details = response && response.videoDetails;
                const microformat = response
                    && response.microformat
                    && response.microformat.playerMicroformatRenderer;
                const channel = String(
                    (details && details.author)
                    || (microformat && microformat.ownerChannelName)
                    || ""
                ).replace(/\s+/g, " ").trim().slice(0, 300);
                if (!Array.isArray(tracks) || tracks.length === 0) {
                    return JSON.stringify({ error: "missing", title, channel });
                }
                const track = tracks.find((item) => item.kind !== "asr") || tracks[0];
                return JSON.stringify({
                    baseUrl: track.baseUrl || "",
                    languageCode: track.languageCode || "",
                    title,
                    channel
                });
            } catch (_) {
                return JSON.stringify({ error: "unavailable" });
            }
        },
        requestCaptions() {
            const button = document.querySelector(
                "button.ytp-subtitles-button, .ytp-subtitles-button"
            );
            if (button) {
                button.click();
                window.setTimeout(() => button.click(), 150);
                return "triggered";
            }
            try {
                const player = playerElement();
                if (player && typeof player.loadModule === "function") {
                    player.loadModule("captions");
                }
                const tracks = player && typeof player.getOption === "function"
                    ? player.getOption("captions", "tracklist") : null;
                if (Array.isArray(tracks)
                        && tracks.length > 0
                        && typeof player.setOption === "function") {
                    const track = tracks.find((item) => item.kind !== "asr") || tracks[0];
                    player.setOption("captions", "track", track);
                    return "triggered";
                }
            } catch (_) {
                // Fall through to the unavailable result.
            }
            return "missing";
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
        currentTime() {
            const video = document.querySelector("video");
            return video && Number.isFinite(video.currentTime) ? video.currentTime : null;
        },
        preparePictureInPicture() {
            return activePictureInPictureMedia() || soundCloudPlaying()
                ? "audio" : "unavailable";
        },
        pictureInPictureState() {
            const media = activePictureInPictureMedia();
            if (!media && !soundCloudPlaying()) {
                return { playing: false, video: false, width: 1, height: 1 };
            }
            const video = media instanceof HTMLVideoElement;
            const bounds = video ? media.getBoundingClientRect() : null;
            const boundedCoordinate = (value) =>
                Number.isFinite(value) ? Math.max(-100000, Math.min(100000, value)) : 0;
            return {
                playing: true,
                video,
                width: video && Number.isFinite(media.videoWidth) ? media.videoWidth : 1,
                height: video && Number.isFinite(media.videoHeight) ? media.videoHeight : 1,
                left: bounds ? boundedCoordinate(bounds.left) : 0,
                top: bounds ? boundedCoordinate(bounds.top) : 0,
                right: bounds ? boundedCoordinate(bounds.right) : 0,
                bottom: bounds ? boundedCoordinate(bounds.bottom) : 0,
                viewportWidth: boundedCoordinate(window.innerWidth),
                viewportHeight: boundedCoordinate(window.innerHeight),
                label: pictureInPictureLabel()
            };
        },
        setPictureInPictureActive(enabled) {
            state.pictureInPictureActive = Boolean(enabled);
            state.pictureInPicturePlaybackRequested = state.pictureInPictureActive;
            return state.pictureInPictureActive;
        },
        setPictureInPicturePlayback(playing) {
            const shouldPlay = Boolean(playing);
            state.pictureInPicturePlaybackRequested = shouldPlay;
            const media = mediaElements().find((element) => !element.ended);
            if (media && media.paused !== shouldPlay) {
                return shouldPlay;
            }
            const player = youtubePlayer();
            if (player) {
                const method = shouldPlay ? player.playVideo : player.pauseVideo;
                if (typeof method === "function") {
                    try {
                        method.call(player);
                    } catch (_) {
                        // Fall through to the media element.
                    }
                }
            }
            if (media) {
                if (shouldPlay) {
                    media.play().catch(() => {});
                } else {
                    media.pause();
                }
                return shouldPlay;
            }
            const button = soundCloudPlaybackButton();
            if (button && soundCloudPlaying() !== shouldPlay) {
                button.click();
            }
            return shouldPlay && Boolean(button);
        },
        status() {
            return {
                speed: state.speed,
                adSkipping: state.adSkipping,
                adShowing: isAdShowing(),
                adaptiveSpeed: state.adaptiveSpeed,
                sponsorSkipping: state.sponsorSkipping,
                sponsorSegments: state.sponsorSegments.length
            };
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
