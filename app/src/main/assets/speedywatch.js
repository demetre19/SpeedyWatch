(() => {
    "use strict";

    const existing = window.__speedyWatchController;
    if (existing && existing.version === 25) {
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
        pictureInPicturePlaybackRequested: false,
        megaBrowserChoiceAt: 0,
        lastFrameCaptionReportAt: 0
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

    const mediaActivity = new WeakMap();
    const remoteFrames = new Map();
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
        const label = `${button.getAttribute("title") || ""} ` +
            `${button.getAttribute("aria-label") || ""}`;
        return button.classList.contains("playing") || /\bpause\b/i.test(label);
    };
    const noteMediaActivity = (media) => {
        if (media instanceof HTMLMediaElement) {
            mediaActivity.set(media, Date.now());
        }
    };
    document.addEventListener("pointerdown", (event) => {
        const target = event.target;
        if (target instanceof HTMLMediaElement) {
            noteMediaActivity(target);
            return;
        }
        const media = target && typeof target.closest === "function"
            ? target.closest("video, audio") : null;
        noteMediaActivity(media);
    }, true);
    document.addEventListener("play", (event) => noteMediaActivity(event.target), true);
    const visibleMediaArea = (media) => {
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
    const mediaStatus = (media) => {
        if (!media) {
            return null;
        }
        const duration = Number.isFinite(media.duration)
            ? Math.max(0, Math.min(604800, media.duration)) : null;
        const currentTime = Number.isFinite(media.currentTime)
            ? Math.max(0, Math.min(duration ?? 604800, media.currentTime)) : null;
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
            lastActiveAt: mediaActivity.get(media) || 0,
            visibleArea: visibleMediaArea(media)
        };
    };
    const compareMediaStatus = (left, right) => {
        const leftRank = [
            left?.playing ? 1 : 0,
            Number(left?.lastActiveAt) || 0,
            Number(left?.visibleArea) || 0,
            left?.ready ? 1 : 0
        ];
        const rightRank = [
            right?.playing ? 1 : 0,
            Number(right?.lastActiveAt) || 0,
            Number(right?.visibleArea) || 0,
            right?.ready ? 1 : 0
        ];
        for (let index = 0; index < leftRank.length; index++) {
            if (leftRank[index] !== rightRank[index]) {
                return leftRank[index] - rightRank[index];
            }
        }
        return 0;
    };
    const selectedLocalMedia = (videoOnly = false) => mediaElements()
        .filter((media) => !videoOnly || media instanceof HTMLVideoElement)
        .reduce((selected, media) => !selected
            || compareMediaStatus(mediaStatus(media), mediaStatus(selected)) > 0
            ? media : selected, null);
    const selectedMediaTarget = (videoOnly = false) => {
        const localMedia = selectedLocalMedia(videoOnly);
        let selected = localMedia
            ? { media: localMedia, status: mediaStatus(localMedia), source: null }
            : null;
        if (window.top !== window) {
            return selected;
        }
        const now = Date.now();
        remoteFrames.forEach((record, source) => {
            if (!record || now - record.receivedAt > 2000
                    || (videoOnly && !record.status.video)) {
                remoteFrames.delete(source);
                return;
            }
            if (!selected || compareMediaStatus(record.status, selected.status) > 0) {
                selected = { media: null, status: record.status, source };
            }
        });
        return selected;
    };
    const postTargetCommand = (target, command, value) => {
        if (!target?.source) {
            return false;
        }
        try {
            target.source.postMessage({
                speedyWatch: 23,
                type: "command",
                command,
                value
            }, "*");
            return true;
        } catch (_) {
            remoteFrames.delete(target.source);
            return false;
        }
    };
    const activePictureInPictureMedia = () => {
        const target = selectedMediaTarget();
        return target?.status?.playing ? target : null;
    };
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

    const currentYouTubeVideoId = () => {
        try {
            const host = window.location.hostname.toLowerCase();
            if (host !== "youtu.be" && host !== "youtube.com" && !host.endsWith(".youtube.com")) {
                return "";
            }
            const value = new URL(window.location.href).searchParams.get("v") || "";
            return /^[A-Za-z0-9_-]{11}$/.test(value) ? value : "";
        } catch (_) {
            return "";
        }
    };

    const youtubeDescription = () => {
        const videoId = currentYouTubeVideoId();
        if (!videoId) {
            return "";
        }
        const responses = [];
        try {
            const player = playerElement();
            if (player && typeof player.getPlayerResponse === "function") {
                responses.push(player.getPlayerResponse());
            }
        } catch (_) {
            // Continue with page-level response data.
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
                // Continue with the other bounded sources.
            }
        }
        for (const response of responses) {
            const details = response && response.videoDetails;
            if (details
                    && details.videoId === videoId
                    && typeof details.shortDescription === "string") {
                return details.shortDescription.slice(0, 100000);
            }
        }
        const selectors = [
            "#description-inline-expander",
            "ytm-expandable-video-description-body-renderer",
            "yt-formatted-string#description",
            "meta[itemprop='description']"
        ];
        for (const selector of selectors) {
            const node = document.querySelector(selector);
            const value = node
                ? (node.getAttribute("content") || node.innerText || node.textContent || "")
                : "";
            if (/(^|\n)\s*0:00\s+\S/m.test(value)) {
                return value.slice(0, 100000);
            }
        }
        return "";
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

    const selectMegaBrowserChoice = () => {
        if (window.location.hostname.toLowerCase() !== "mega.nz") {
            return false;
        }
        const now = Date.now();
        if (now - state.megaBrowserChoiceAt < 2000) {
            return false;
        }
        const labelPattern = /\b(?:open|continue|stay) in (?:this )?browser\b/i;
        const candidates = document.querySelectorAll(
            "button, a, [role='button'], input[type='button'], input[type='submit'], " +
            ".mobile.red-button, .mobile.cta-button, [class*='browser']"
        );
        for (const element of candidates) {
            const label = [
                element.innerText,
                element.textContent,
                element.getAttribute("aria-label"),
                element.getAttribute("title"),
                element.value
            ].filter(Boolean).join(" ").replace(/\s+/g, " ").trim();
            if (!labelPattern.test(label)
                    || element.disabled
                    || element.getAttribute("aria-disabled") === "true") {
                continue;
            }
            const style = window.getComputedStyle(element);
            const bounds = element.getBoundingClientRect();
            if (style.display === "none"
                    || style.visibility === "hidden"
                    || Number(style.opacity) === 0
                    || bounds.width <= 0
                    || bounds.height <= 0) {
                continue;
            }
            state.megaBrowserChoiceAt = now;
            element.dispatchEvent(new CustomEvent("tap", {
                bubbles: true,
                cancelable: true
            }));
            element.click();
            return true;
        }
        return false;
    };

    const megaFolderName = () => {
        if (window.location.hostname.toLowerCase() !== "mega.nz") {
            return "";
        }
        try {
            const currentId = window.M && window.M.currentdirid;
            const currentNode = currentId && window.M.d && window.M.d[currentId];
            if (currentNode && currentNode.t && typeof currentNode.name === "string") {
                const name = currentNode.name.replace(/\s+/g, " ").trim();
                if (name) {
                    return name.slice(0, 120);
                }
            }
        } catch (_) {
            // Fall through to the rendered breadcrumb.
        }
        const selectors = [
            ".fm-breadcrumbs-block .fm-breadcrumbs .selectable-txt",
            ".mobile.fm-header-txt span",
            ".mobile .fm-header-txt span"
        ];
        for (const selector of selectors) {
            const elements = Array.from(document.querySelectorAll(selector));
            for (let index = elements.length - 1; index >= 0; index--) {
                const element = elements[index];
                const style = window.getComputedStyle(element);
                const name = (element.textContent || "").replace(/\s+/g, " ").trim();
                if (name && !/^mega$/i.test(name)
                        && style.display !== "none"
                        && style.visibility !== "hidden") {
                    return name.slice(0, 120);
                }
            }
        }
        return "";
    };


    const X_SHORT_LINK_PATTERN = /^https?:\/\/t\.co\/[A-Za-z0-9_-]+\/?$/i;
    const X_DOMAIN_TEXT_PATTERN =
        /^(?:https?:\/\/)?[a-z0-9][a-z0-9-]*(?:\.[a-z0-9-]+)+(?::\d+)?(?:\/\S*)?$/i;

    const onXSite = () => {
        const host = window.location.hostname.toLowerCase();
        return host === "x.com" || host.endsWith(".x.com")
            || host === "twitter.com" || host.endsWith(".twitter.com");
    };

    // X shows the unwrapped destination in the visible link text (for example
    // "huggingface.co/VextLabsinc" over a t.co href). Normalize that text to an HTTPS
    // candidate; native validation decides whether it is a real URL.
    const xUrlHint = (anchor) => {
        const candidates = [anchor.getAttribute("data-expanded-url"), anchor.textContent];
        for (const candidate of candidates) {
            const trimmed = (candidate || "").replace(/\s+/g, " ").trim();
            if (!trimmed || !X_DOMAIN_TEXT_PATTERN.test(trimmed)) {
                continue;
            }
            const expanded = /^https?:\/\//i.test(trimmed)
                ? trimmed : "https://" + trimmed;
            if (!X_SHORT_LINK_PATTERN.test(expanded)) {
                return expanded.slice(0, 2000);
            }
        }
        return "";
    };

    const xPostedAt = (anchor) => {
        const container = anchor.closest(
            '[data-testid="tweet"], [data-testid="messageEntry"]'
        );
        const scoped = container ? container.querySelector("time[datetime]") : null;
        if (scoped) {
            return scoped.getAttribute("datetime") || "";
        }
        // Chat messages often expose only date dividers: use the nearest preceding time.
        const scope = anchor.closest('[data-testid="DmConversation"], main') || document;
        const times = scope.querySelectorAll("time[datetime]");
        let found = "";
        for (const time of times) {
            if (time.compareDocumentPosition(anchor) & Node.DOCUMENT_POSITION_FOLLOWING) {
                found = time.getAttribute("datetime") || "";
            } else {
                break;
            }
        }
        return found;
    };

    const xPosterName = (anchor) => {
        const container = anchor.closest('[data-testid="tweet"]');
        const names = container
            ? container.querySelector('[data-testid="User-Names"]')
            : null;
        return names
            ? (names.textContent || "").replace(/\s+/g, " ").trim().slice(0, 120)
            : "";
    };

    const collectXLinks = () => {
        if (!onXSite()) {
            return JSON.stringify({ links: [] });
        }
        // Live X markup changes often, so harvesting stays container-agnostic: every
        // outbound http(s) anchor counts (t.co short links, external hosts, and shared
        // x.com status links); purely internal navigation links are ignored.
        const anchors = document.querySelectorAll("a[href]");
        const seen = {};
        const links = [];
        for (const anchor of anchors) {
            if (links.length >= 500) {
                break;
            }
            const rawHref = anchor.getAttribute("href") || "";
            if (!/^https?:\/\//i.test(rawHref) || seen[rawHref]) {
                continue;
            }
            const internal = /^https?:\/\/(?:www\.)?(?:x|twitter)\.com(?:\/|$)/i.test(rawHref);
            const statusLink =
                /^https?:\/\/(?:www\.)?(?:x|twitter)\.com\/[^/]+\/status\/\d+/i.test(rawHref);
            if (internal && !statusLink) {
                continue;
            }
            // Only links the user is actually looking at: anchor must intersect the
            // current viewport instead of harvesting the whole rendered history.
            const bounds = anchor.getBoundingClientRect();
            if (bounds.bottom <= 0 || bounds.top >= window.innerHeight
                    || bounds.right <= 0 || bounds.left >= window.innerWidth) {
                continue;
            }
            if (internal && !statusLink) {
                continue;
            }
            seen[rawHref] = true;
            links.push({
                url: rawHref.slice(0, 2000),
                text: (anchor.textContent || "").replace(/\s+/g, " ").trim().slice(0, 500),
                hint: xUrlHint(anchor),
                poster: xPosterName(anchor),
                at: xPostedAt(anchor).slice(0, 64)
            });
        }
        return JSON.stringify({
            links,
            pageUrl: String(window.location.href).slice(0, 2000)
        });
    };
    const collectPageLinks = () => {
        if (onXSite()) {
            return collectXLinks();
        }
        const anchors = document.querySelectorAll("a[href]");
        const seen = {};
        const links = [];
        for (const anchor of anchors) {
            if (links.length >= 500) {
                break;
            }
            const rawHref = anchor.getAttribute("href") || "";
            if (!rawHref || rawHref.startsWith("#")) {
                continue;
            }
            let absoluteHref;
            try {
                absoluteHref = new URL(rawHref, window.location.href).href;
            } catch (error) {
                continue;
            }
            if (!/^https:\/\//i.test(absoluteHref) || seen[absoluteHref]) {
                continue;
            }
            const bounds = anchor.getBoundingClientRect();
            if (bounds.bottom <= 0 || bounds.top >= window.innerHeight
                    || bounds.right <= 0 || bounds.left >= window.innerWidth) {
                continue;
            }
            seen[absoluteHref] = true;
            links.push({
                url: absoluteHref.slice(0, 2000),
                text: (anchor.textContent || "").replace(/\s+/g, " ").trim().slice(0, 500),
                hint: "",
                poster: "",
                at: ""
            });
        }
        return JSON.stringify({
            links,
            pageUrl: String(window.location.href).slice(0, 2000)
        });
    };
    // X posts and articles expose their content as rendered DOM text rather than
    // captions, so summarizing reads the visible self-thread or article body.
    const collectXPageText = () => {
        if (!onXSite()) {
            return JSON.stringify({ kind: "", author: "", title: "", blocks: [] });
        }
        const clean = (value) => String(value || "").replace(/\s+/g, " ").trim();
        const noise = /^(?:follow|following|reply|replies|repost(?:s|ed)?|like[d]?|views?|share|copy link|see more|show this thread|show more|show less|less|article|save|pinned|translate post|read \d+ r(?:eply|eplies)|\d+(?:\/\d+)?|home|search|explore|notifications|messages|grok|premium|bookmarks|lists|profile|more|settings|highlight|who to follow|subscribe to premium|verified organizations|create account|sign up|log in|terms of service|privacy policy|cookie policy|accessibility|ads info)$/;
        const blocks = [];
        let total = 0;
        const push = (value) => {
            const text = clean(value).slice(0, 2000);
            if (text.length < 2 || noise.test(text)
                    || blocks.length >= 400 || total + text.length > 40000) {
                return;
            }
            if (blocks.length && blocks[blocks.length - 1] === text) {
                return;
            }
            blocks.push(text);
            total += text.length;
        };
        let kind = "";
        let author = "";
        let title = "";
        const tweets = document.querySelectorAll('article[data-testid="tweet"]');
        if (tweets.length > 0) {
            kind = "post";
            const handleOf = (tweet) => {
                const link = tweet.querySelector('a[href^="/"][href*="/status/"]');
                const match = link
                    ? /^\/([A-Za-z0-9_]{1,20})(?:\/|$)/.exec(link.getAttribute("href") || "")
                    : null;
                return match ? match[1].toLowerCase() : "";
            };
            author = handleOf(tweets[0]);
            let included = 0;
            for (const tweet of tweets) {
                if (blocks.length >= 400 || total >= 40000) {
                    break;
                }
                const body = tweet.querySelector('[data-testid="tweetText"]');
                if (!body) {
                    continue;
                }
                const handle = handleOf(tweet);
                // Self-thread only: once the focused post is captured, replies
                // from other accounts stay out of the extracted text.
                if (included > 0 && ((!author && !handle) || (author && handle !== author))) {
                    break;
                }
                included += 1;
                push(body.innerText);
            }
        }
        if (blocks.length === 0) {
            // Tier 2: rendered post text nodes wherever X's container markup
            // puts them; article shells can render without matching testids.
            const bodies = document.querySelectorAll('[data-testid="tweetText"]');
            if (bodies.length > 0) {
                kind = kind || "post";
                for (const body of bodies) {
                    if (blocks.length >= 400 || total >= 40000) {
                        break;
                    }
                    push(body.innerText);
                }
            }
        }
        if (blocks.length === 0) {
            // Tier 3: long-form article body or any readable primary region.
            const container =
                document.querySelector('[data-testid="articleContent"]')
                || document.querySelector("article")
                || document.querySelector('[role="dialog"]')
                || document.querySelector('[data-testid="primaryColumn"]')
                || document.querySelector("main");
            if (container) {
                kind = "article";
                const heading = container.querySelector("h1");
                title = heading ? clean(heading.innerText).slice(0, 200) : "";
                const parts = container.querySelectorAll("h2, h3, p, blockquote, li");
                if (parts.length >= 3) {
                    for (const part of parts) {
                        if (part.querySelector("h2, h3, p, blockquote")) {
                            continue; // Leaf blocks only; containers duplicate text.
                        }
                        push(part.innerText);
                    }
                } else {
                    for (const line of String(container.innerText || "").split("\n")) {
                        push(line);
                    }
                }
            }
        }
        return JSON.stringify({
            kind,
            author: author.slice(0, 40),
            title,
            blocks,
            pageUrl: String(window.location.href).slice(0, 2000)
        });
    };

    const captionEntries = (media) => {
        if (!media || !media.textTracks) {
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
                    start: Number.isFinite(cue.startTime)
                        ? Math.max(0, Math.min(604800, cue.startTime)) : null,
                    end: Number.isFinite(cue.endTime)
                        ? Math.max(0, Math.min(604800, cue.endTime)) : null,
                    text
                });
            }
            if (entries.length > 0) {
                break;
            }
        }
        return entries;
    };
    const readablePageBlocks = () => {
        const candidates = Array.from(document.querySelectorAll(
            "article, main, [role='main']"
        ));
        const root = candidates.reduce((best, candidate) => {
            const length = String(candidate.innerText || "").length;
            const bestLength = best ? String(best.innerText || "").length : 0;
            return length > bestLength ? candidate : best;
        }, null) || document.body;
        if (!root) {
            return [];
        }
        const blocks = [];
        const seen = new Set();
        let characters = 0;
        const noise = /^(?:accept|reject|manage cookies|sign in|log in|subscribe|menu|close)$/i;
        const nodes = root.querySelectorAll("h1, h2, h3, p, blockquote, li, pre");
        for (const node of nodes) {
            if (blocks.length >= 400 || characters >= 40000
                    || node.closest("nav, header, footer, aside, form, dialog, [aria-hidden='true']")) {
                continue;
            }
            const style = window.getComputedStyle(node);
            const bounds = node.getBoundingClientRect();
            if (style.display === "none" || style.visibility === "hidden"
                    || bounds.width <= 0 || bounds.height <= 0) {
                continue;
            }
            const text = String(node.innerText || node.textContent || "")
                .replace(/\s+/g, " ").trim().slice(0, 4000);
            const key = text.toLowerCase();
            if (!text || text.length < 2 || noise.test(text) || seen.has(key)) {
                continue;
            }
            seen.add(key);
            characters += text.length;
            if (characters > 40000) {
                break;
            }
            blocks.push(text);
        }
        return blocks;
    };
    const collectPageContent = () => {
        const target = selectedMediaTarget();
        let captions = [];
        if (target?.source) {
            captions = remoteFrames.get(target.source)?.captions || [];
        } else if (target?.media) {
            captions = captionEntries(target.media);
        }
        const title = String(document.title || "").replace(/\s+/g, " ").trim().slice(0, 300);
        const authorNode = document.querySelector(
            "meta[name='author'], meta[property='article:author']"
        );
        const author = String(authorNode?.getAttribute("content") || "")
            .replace(/\s+/g, " ").trim().slice(0, 300);
        return JSON.stringify({
            kind: captions.length > 0 ? "captions" : "page",
            author,
            title,
            blocks: captions.length > 0
                ? captions.map((entry) => entry.text) : readablePageBlocks(),
            pageUrl: String(window.location.href).slice(0, 2000)
        });
    };
    // Facebook bakes player data (progressive renditions, DASH manifests) into
    // inline page payloads instead of fetching a manifest URL, so request
    // interception never sees a media request; dig those URLs out of the
    // inline script nodes. Rendered Facebook documents serialize far past any
    // single-pass cap, so scan script-by-script under a total byte budget
    // instead of bailing on document size.
    const facebookMedia = () => {
        const host = String(window.location.hostname || "").toLowerCase();
        if (!(host === "facebook.com" || host.endsWith(".facebook.com"))) {
            return JSON.stringify({ url: "" });
        }
        let scripts = [];
        try {
            scripts = Array.prototype.slice.call(document.querySelectorAll("script"));
        } catch (error) {
            return JSON.stringify({ url: "" });
        }
        const pattern = /"(?:browser_native_hd_url|browser_native_sd_url|playable_url|hd_src|sd_src)"\s*:\s*"((?:[^"\\]|\\.)*)"/;
        let budget = 12000000;
        for (let index = 0; index < scripts.length && budget > 0; index++) {
            let text = "";
            try {
                text = String(scripts[index].textContent || "");
            } catch (error) {
                continue;
            }
            if (!text) {
                continue;
            }
            budget -= text.length;
            const sources = [text];
            const unescaped = text.replace(/\\"/g, '"');
            if (unescaped !== text) {
                sources.push(unescaped);
            }
            for (const source of sources) {
                const match = pattern.exec(source);
                if (!match) {
                    continue;
                }
                try {
                    const decoded = JSON.parse('"' + match[1] + '"');
                    if (/^https:\/\/[^\s"<>]+$/.test(decoded)) {
                        return JSON.stringify({
                            url: decoded.slice(0, 2000)
                        });
                    }
                } catch (error) {
                    /* keep scanning */
                }
            }
        }
        return JSON.stringify({ url: "" });
    };
    const tick = () => {
        state.pending = false;
        selectMegaBrowserChoice();
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
        version: 25,
        megaFolderName,
        collectXLinks,
        collectPageLinks,
        collectXPageText,
        collectPageContent,
        facebookMedia,
        setSpeed(value) {
            const parsed = Number(value);
            if (!Number.isFinite(parsed)) {
                return state.speed;
            }
            state.speed = Math.min(4, Math.max(0.25, parsed));
            tick();
            if (window.top === window) {
                remoteFrames.forEach((_, source) =>
                    postTargetCommand({ source }, "setSpeed", state.speed));
            }
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
            const target = selectedMediaTarget();
            if (!target || !target.status.seekable || !Number.isFinite(parsed)) {
                return false;
            }
            if (target.source) {
                return postTargetCommand(target, "seekTo", parsed);
            }
            const maximum = Number.isFinite(target.media.duration) && target.media.duration >= 0
                ? Math.min(604800, target.media.duration)
                : 604800;
            try {
                target.media.currentTime = Math.min(maximum, Math.max(0, parsed));
                return true;
            } catch (_) {
                return false;
            }
        },
        currentTime() {
            const target = selectedMediaTarget();
            return target?.status?.seekable && Number.isFinite(target.status.currentTime)
                ? target.status.currentTime : null;
        },
        chapterContext() {
            const video = selectedLocalMedia(true);
            const currentTime = video && Number.isFinite(video.currentTime)
                ? video.currentTime : null;
            const duration = video && Number.isFinite(video.duration)
                ? video.duration : null;
            return {
                videoId: currentYouTubeVideoId(),
                description: youtubeDescription(),
                currentTime,
                duration
            };
        },
        togglePlayback() {
            const target = selectedMediaTarget();
            const soundCloudButton = soundCloudPlaybackButton();
            if (!target && !soundCloudButton) {
                return "unavailable";
            }
            if (target?.source) {
                const shouldPlay = Boolean(target.status.paused || target.status.ended);
                return postTargetCommand(target, "togglePlayback", null)
                    ? (shouldPlay ? "playing" : "paused") : "unavailable";
            }
            const media = target?.media || null;
            const shouldPlay = media ? media.paused : !soundCloudPlaying();
            if (media) {
                if (shouldPlay) {
                    media.play().catch(() => {});
                } else {
                    media.pause();
                }
            } else {
                soundCloudButton.click();
            }
            return shouldPlay ? "playing" : "paused";
        },
        preparePictureInPicture() {
            return activePictureInPictureMedia() || soundCloudPlaying()
                ? "audio" : "unavailable";
        },
        pictureInPictureState() {
            const target = selectedMediaTarget();
            const soundCloudActive = soundCloudPlaying();
            if (!target && !soundCloudActive) {
                return { playing: false, video: false, width: 1, height: 1 };
            }
            const media = target?.media || null;
            const status = target?.status || {};
            const video = Boolean(status.video);
            const bounds = media && video ? media.getBoundingClientRect() : null;
            const boundedCoordinate = (value) =>
                Number.isFinite(value) ? Math.max(-100000, Math.min(100000, value)) : 0;
            return {
                playing: Boolean(status.playing || soundCloudActive),
                video,
                width: media instanceof HTMLVideoElement && Number.isFinite(media.videoWidth)
                    ? media.videoWidth : Math.max(1, Number(status.width) || 1),
                height: media instanceof HTMLVideoElement && Number.isFinite(media.videoHeight)
                    ? media.videoHeight : Math.max(1, Number(status.height) || 1),
                left: bounds ? boundedCoordinate(bounds.left)
                    : boundedCoordinate(status.left),
                top: bounds ? boundedCoordinate(bounds.top)
                    : boundedCoordinate(status.top),
                right: bounds ? boundedCoordinate(bounds.right)
                    : boundedCoordinate(status.right),
                bottom: bounds ? boundedCoordinate(bounds.bottom)
                    : boundedCoordinate(status.bottom),
                viewportWidth: boundedCoordinate(window.innerWidth),
                viewportHeight: boundedCoordinate(window.innerHeight),
                label: typeof status.label === "string"
                    ? status.label.slice(0, 40) : pictureInPictureLabel()
            };
        },
        setPictureInPictureActive(enabled) {
            state.pictureInPictureActive = Boolean(enabled);
            state.pictureInPicturePlaybackRequested = state.pictureInPictureActive;
            const target = selectedMediaTarget();
            if (target?.source) {
                postTargetCommand(target, "setPictureInPictureActive",
                    state.pictureInPictureActive);
            }
            return state.pictureInPictureActive;
        },
        setPictureInPicturePlayback(playing) {
            const shouldPlay = Boolean(playing);
            state.pictureInPicturePlaybackRequested = shouldPlay;
            const target = selectedMediaTarget();
            if (target?.source) {
                return postTargetCommand(target, "setPictureInPicturePlayback", shouldPlay);
            }
            const media = target?.media || null;
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
            const target = selectedMediaTarget();
            return {
                speed: state.speed,
                hasMedia: Boolean(target),
                mediaPlaying: Boolean(target?.status?.playing),
                adSkipping: state.adSkipping,
                adShowing: isAdShowing(),
                adaptiveSpeed: state.adaptiveSpeed,
                sponsorSkipping: state.sponsorSkipping,
                sponsorSegments: state.sponsorSegments.length
            };
        }
    };

    window.__speedyWatchController = api;

    const boundedFrameStatus = (value) => {
        if (!value || typeof value !== "object") {
            return null;
        }
        const boundedNumber = (number, minimum, maximum, fallback = 0) =>
            Number.isFinite(Number(number))
                ? Math.max(minimum, Math.min(maximum, Number(number))) : fallback;
        return {
            playing: value.playing === true,
            paused: value.paused !== false,
            ended: value.ended === true,
            ready: value.ready === true,
            video: value.video === true,
            seekable: value.seekable === true,
            currentTime: value.currentTime == null ? null
                : boundedNumber(value.currentTime, 0, 604800),
            duration: value.duration == null ? null
                : boundedNumber(value.duration, 0, 604800),
            rate: boundedNumber(value.rate, 0.25, 4, 1),
            lastActiveAt: boundedNumber(value.lastActiveAt, 0, Date.now()),
            visibleArea: boundedNumber(value.visibleArea, 0, 100000000),
            width: boundedNumber(value.width, 1, 100000, 1),
            height: boundedNumber(value.height, 1, 100000, 1),
            left: boundedNumber(value.left, -100000, 100000),
            top: boundedNumber(value.top, -100000, 100000),
            right: boundedNumber(value.right, -100000, 100000),
            bottom: boundedNumber(value.bottom, -100000, 100000),
            label: typeof value.label === "string" ? value.label.slice(0, 40) : "Web video"
        };
    };
    const boundedCaptionEntries = (value) => {
        if (!Array.isArray(value)) {
            return null;
        }
        const entries = [];
        let characters = 0;
        for (const entry of value.slice(0, 1000)) {
            const text = String(entry?.text || "").replace(/\s+/g, " ").trim().slice(0, 2000);
            if (!text) {
                continue;
            }
            characters += text.length;
            if (characters > 40000) {
                break;
            }
            entries.push({
                start: Number.isFinite(Number(entry.start))
                    ? Math.max(0, Math.min(604800, Number(entry.start))) : null,
                end: Number.isFinite(Number(entry.end))
                    ? Math.max(0, Math.min(604800, Number(entry.end))) : null,
                text
            });
        }
        return entries;
    };
    const reportFrameStatus = () => {
        if (window.top === window) {
            return;
        }
        const media = selectedLocalMedia();
        if (!media) {
            return;
        }
        const status = mediaStatus(media);
        if (media instanceof HTMLVideoElement) {
            const bounds = media.getBoundingClientRect();
            status.width = Number.isFinite(media.videoWidth) ? media.videoWidth : 1;
            status.height = Number.isFinite(media.videoHeight) ? media.videoHeight : 1;
            status.left = bounds.left;
            status.top = bounds.top;
            status.right = bounds.right;
            status.bottom = bounds.bottom;
        }
        status.label = pictureInPictureLabel();
        const message = {
            speedyWatch: 23,
            type: "status",
            status
        };
        if (Date.now() - state.lastFrameCaptionReportAt >= 2000) {
            state.lastFrameCaptionReportAt = Date.now();
            message.captions = captionEntries(media);
        }
        window.top.postMessage(message, "*");
    };
    window.addEventListener("message", (event) => {
        const message = event.data;
        if (!message || message.speedyWatch !== 23) {
            return;
        }
        if (window.top === window && message.type === "status" && event.source) {
            const status = boundedFrameStatus(message.status);
            if (status) {
                const previous = remoteFrames.get(event.source);
                const captions = message.captions === undefined
                    ? (previous?.captions || [])
                    : (boundedCaptionEntries(message.captions) || []);
                remoteFrames.set(event.source, {
                    status,
                    captions,
                    receivedAt: Date.now()
                });
                if (Math.abs(status.rate - state.speed) > 0.005) {
                    postTargetCommand({ source: event.source }, "setSpeed", state.speed);
                }
            }
            return;
        }
        if (window.top !== window && event.source === window.top
                && message.type === "command") {
            switch (message.command) {
                case "setSpeed":
                    api.setSpeed(message.value);
                    break;
                case "seekTo":
                    api.seekTo(message.value);
                    break;
                case "togglePlayback":
                    api.togglePlayback();
                    break;
                case "setPictureInPictureActive":
                    api.setPictureInPictureActive(message.value);
                    break;
                case "setPictureInPicturePlayback":
                    api.setPictureInPicturePlayback(message.value);
                    break;
                default:
                    break;
            }
        }
    }, false);

    document.addEventListener("playing", scheduleTick, true);
    document.addEventListener("loadeddata", scheduleTick, true);
    document.addEventListener("ratechange", scheduleTick, true);
    document.addEventListener("yt-navigate-finish", scheduleTick, true);

    const observer = new MutationObserver(scheduleTick);
    const startObserver = () => {
        if (!document.documentElement) {
            return false;
        }
        observer.observe(document.documentElement, {
            attributes: true,
            attributeFilter: ["class"],
            childList: true,
            subtree: true
        });
        return true;
    };
    if (!startObserver()) {
        document.addEventListener("readystatechange", startObserver, { once: true });
    }

    state.timer = window.setInterval(() => {
        tick();
        reportFrameStatus();
    }, 500);
    tick();
    reportFrameStatus();
    return "installed";
})();
