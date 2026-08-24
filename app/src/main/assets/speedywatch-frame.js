(() => {
    "use strict";
    if (window.top === window || window.__speedyWatchFrameController?.version === 1) {
        return;
    }

    const state = {
        speed: 1,
        pictureInPictureActive: false,
        pictureInPicturePlaybackRequested: false,
        lastCaptionReportAt: 0
    };
    const activity = new WeakMap();
    const mediaElements = () => Array.from(document.querySelectorAll("video, audio"));
    const noteActivity = (media) => {
        if (media instanceof HTMLMediaElement) {
            activity.set(media, Date.now());
        }
    };
    document.addEventListener("pointerdown", (event) => {
        const target = event.target;
        noteActivity(target instanceof HTMLMediaElement
            ? target
            : target && typeof target.closest === "function"
                ? target.closest("video, audio") : null);
    }, true);
    document.addEventListener("play", (event) => noteActivity(event.target), true);

    const visibleArea = (media) => {
        try {
            const bounds = media.getBoundingClientRect();
            const width = Math.max(0, Math.min(bounds.right, window.innerWidth)
                - Math.max(bounds.left, 0));
            const height = Math.max(0, Math.min(bounds.bottom, window.innerHeight)
                - Math.max(bounds.top, 0));
            return Math.min(100000000, width * height);
        } catch (_) {
            return 0;
        }
    };
    const status = (media) => {
        const duration = Number.isFinite(media.duration)
            ? Math.max(0, Math.min(604800, media.duration)) : null;
        const currentTime = Number.isFinite(media.currentTime)
            ? Math.max(0, Math.min(duration ?? 604800, media.currentTime)) : null;
        const bounds = media instanceof HTMLVideoElement
            ? media.getBoundingClientRect() : null;
        return {
            playing: !media.paused && !media.ended
                && media.readyState >= HTMLMediaElement.HAVE_CURRENT_DATA,
            paused: media.paused,
            ended: media.ended,
            ready: media.readyState >= HTMLMediaElement.HAVE_METADATA,
            video: media instanceof HTMLVideoElement,
            seekable: Boolean(media.seekable && media.seekable.length),
            currentTime,
            duration,
            rate: Number.isFinite(media.playbackRate) ? media.playbackRate : 1,
            lastActiveAt: activity.get(media) || 0,
            visibleArea: visibleArea(media),
            width: media instanceof HTMLVideoElement && Number.isFinite(media.videoWidth)
                ? media.videoWidth : 1,
            height: media instanceof HTMLVideoElement && Number.isFinite(media.videoHeight)
                ? media.videoHeight : 1,
            left: bounds?.left || 0,
            top: bounds?.top || 0,
            right: bounds?.right || 0,
            bottom: bounds?.bottom || 0,
            label: String(window.location.hostname || "Web video").slice(0, 40)
        };
    };
    const compare = (left, right) => {
        const a = [left.playing ? 1 : 0, left.lastActiveAt, left.visibleArea, left.ready ? 1 : 0];
        const b = [right.playing ? 1 : 0, right.lastActiveAt, right.visibleArea, right.ready ? 1 : 0];
        for (let index = 0; index < a.length; index++) {
            if (a[index] !== b[index]) {
                return a[index] - b[index];
            }
        }
        return 0;
    };
    const selectedMedia = (items = mediaElements()) => items.reduce((selected, media) =>
        !selected || compare(status(media), status(selected)) > 0 ? media : selected, null);
    const applySpeed = (items = mediaElements()) => {
        for (const media of items) {
            try {
                media.defaultPlaybackRate = state.speed;
                if (media.playbackRate !== state.speed) {
                    media.playbackRate = state.speed;
                }
                if ("preservesPitch" in media) {
                    media.preservesPitch = true;
                }
            } catch (_) {
                // A later pass handles replaced or temporarily detached media.
            }
        }
    };
    const captions = (media) => {
        if (!media?.textTracks) {
            return [];
        }
        const entries = [];
        let characters = 0;
        for (const track of Array.from(media.textTracks).slice(0, 20)) {
            let cues;
            try {
                cues = track.cues ? Array.from(track.cues) : [];
            } catch (_) {
                continue;
            }
            for (const cue of cues.slice(0, 1000 - entries.length)) {
                const text = String(cue.text || "").replace(/<[^>]+>/g, " ")
                    .replace(/\s+/g, " ").trim().slice(0, 2000);
                if (!text) {
                    continue;
                }
                characters += text.length;
                if (characters > 40000) {
                    return entries;
                }
                entries.push({
                    start: Number.isFinite(cue.startTime) ? cue.startTime : null,
                    end: Number.isFinite(cue.endTime) ? cue.endTime : null,
                    text
                });
            }
            if (entries.length > 0) {
                break;
            }
        }
        return entries;
    };
    const report = () => {
        const items = mediaElements();
        if (items.length === 0) {
            return false;
        }
        applySpeed(items);
        const media = selectedMedia(items);
        const message = { speedyWatch: 23, type: "status", status: status(media) };
        if (Date.now() - state.lastCaptionReportAt >= 2000) {
            state.lastCaptionReportAt = Date.now();
            message.captions = captions(media);
        }
        window.top.postMessage(message, "*");
        return true;
    };
    window.addEventListener("message", (event) => {
        const message = event.data;
        if (event.source !== window.top || !message || message.speedyWatch !== 23
                || message.type !== "command") {
            return;
        }
        const media = selectedMedia();
        switch (message.command) {
            case "setSpeed": {
                const value = Number(message.value);
                if (Number.isFinite(value)) {
                    state.speed = Math.max(0.25, Math.min(4, value));
                    applySpeed();
                }
                break;
            }
            case "seekTo": {
                const value = Number(message.value);
                if (media && media.seekable?.length && Number.isFinite(value)) {
                    const maximum = Number.isFinite(media.duration)
                        ? Math.min(604800, media.duration) : 604800;
                    try {
                        media.currentTime = Math.max(0, Math.min(maximum, value));
                    } catch (_) {
                        // The next status report keeps the native control honest.
                    }
                }
                break;
            }
            case "togglePlayback":
                if (media) {
                    if (media.paused) {
                        media.play().catch(() => {});
                    } else {
                        media.pause();
                    }
                }
                break;
            case "setPictureInPictureActive":
                state.pictureInPictureActive = Boolean(message.value);
                state.pictureInPicturePlaybackRequested = state.pictureInPictureActive;
                break;
            case "setPictureInPicturePlayback":
                state.pictureInPicturePlaybackRequested = Boolean(message.value);
                if (media) {
                    if (state.pictureInPicturePlaybackRequested) {
                        media.play().catch(() => {});
                    } else {
                        media.pause();
                    }
                }
                break;
            default:
                break;
        }
        window.setTimeout(report, 0);
    }, false);

    let reportTimer = 0;
    const scheduleReport = (delay = 100) => {
        if (reportTimer) {
            return;
        }
        reportTimer = window.setTimeout(() => {
            reportTimer = 0;
            if (report()) {
                scheduleReport(750);
            }
        }, delay);
    };
    const observeMediaInsertions = () => {
        if (!document.documentElement) {
            return false;
        }
        const observer = new MutationObserver((records) => {
            const addedMedia = records.some((record) => Array.from(record.addedNodes)
                .some((node) => node instanceof HTMLMediaElement
                    || (node instanceof Element && node.querySelector("video, audio"))));
            if (addedMedia) {
                scheduleReport();
            }
        });
        observer.observe(document.documentElement, { childList: true, subtree: true });
        scheduleReport();
        return true;
    };
    document.addEventListener("loadedmetadata", () => scheduleReport(), true);
    document.addEventListener("play", () => scheduleReport(), true);
    document.addEventListener("emptied", () => scheduleReport(), true);
    if (!observeMediaInsertions()) {
        document.addEventListener("DOMContentLoaded", observeMediaInsertions, { once: true });
    }
    window.__speedyWatchFrameController = { version: 1 };
})();
