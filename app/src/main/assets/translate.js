(() => {
    "use strict";

    const existing = window.__speedyWatchPageTranslator;
    if (existing) {
        return "reused";
    }

    const MAX_TEXT_LENGTH = 500;
    const MAX_QUEUE_LENGTH = 400;
    const SAMPLE_LIMIT = 1500;
    const LETTER = /\p{L}/u;
    const cache = new Map();
    const failed = new Set();
    const sameLanguage = new Set();
    const queuedNodes = new WeakSet();
    const queue = [];
    let activeRequests = 0;
    let scanTimer = 0;
    let translatedCount = 0;
    let targetLanguage = "en";
    let pageCheckDone = false;
    let pageIsTarget = false;

    const NONLATIN_TARGETS = new Set(
        ["ar", "bg", "bn", "el", "fa", "he", "hi", "ja", "ko",
         "ru", "th", "uk", "zh"]);
    const NONLATIN_LETTER = /(?!\p{Script=Latin})\p{L}/u;
    const targetIsLatin = () => !NONLATIN_TARGETS.has(normalized(targetLanguage));

    const normalized = (code) => (code || "").toLowerCase().split("-")[0];

    const isExcluded = (node) => {
        const parent = node && node.parentElement;
        if (!parent || !parent.isConnected) {
            return true;
        }
        if (parent.closest(
            "script, style, noscript, template, input, textarea, select, option, code, pre, " +
            "[contenteditable=''], [contenteditable='true'], [translate='no'], .notranslate, " +
            "[hidden], [aria-hidden='true']"
        )) {
            return true;
        }
        const style = window.getComputedStyle(parent);
        return style.display === "none" || style.visibility === "hidden";
    };

    const translatedText = (data) => {
        if (!Array.isArray(data) || !Array.isArray(data[0])) {
            return "";
        }
        return data[0]
            .map((part) => Array.isArray(part) && typeof part[0] === "string" ? part[0] : "")
            .join("")
            .trim();
    };

    const applyTranslation = (item, translation) => {
        if (!item.node.isConnected || isExcluded(item.node)) {
            return;
        }
        const current = item.node.nodeValue || "";
        if (current.trim() !== item.text) {
            return;
        }
        const leading = current.match(/^\s*/)?.[0] || "";
        const trailing = current.match(/\s*$/)?.[0] || "";
        item.node.nodeValue = leading + translation + trailing;
        translatedCount += 1;
    };

    let requestSeq = 0;
    const pendingRequests = new Map();

    // Page fetch cannot reach the translate endpoint on sites with a strict
    // connect-src CSP (github.com blocks it), so requests go through the
    // console channel which CSP does not cover; native answers via
    // __swTranslateDeliver.
    const requestTranslation = (text) => new Promise((resolve, reject) => {
        const id = ++requestSeq;
        const timer = window.setTimeout(() => {
            pendingRequests.delete(id);
            reject();
        }, 20000);
        pendingRequests.set(id, {resolve, timer});
        try {
            console.log("SWTL|" + id + "|" + targetLanguage + "|" + text);
        } catch (error) {
            window.clearTimeout(timer);
            pendingRequests.delete(id);
            reject();
        }
    });

    window.__swTranslateDeliver = (id, body) => {
        const entry = pendingRequests.get(id);
        if (!entry) {
            return;
        }
        pendingRequests.delete(id);
        window.clearTimeout(entry.timer);
        try {
            entry.resolve(typeof body === "string" ? JSON.parse(body) : null);
        } catch (error) {
            entry.resolve(null);
        }
    };

    let pumpDelay = 200;

    const pump = () => {
        while (activeRequests < 2 && queue.length > 0) {
            const item = queue.shift();
            queuedNodes.delete(item.node);
            if (!item.node.isConnected || isExcluded(item.node)) {
                continue;
            }
            const key = targetLanguage + "|" + item.text;
            const cached = cache.get(key);
            if (cached) {
                applyTranslation(item, cached);
                continue;
            }
            if (failed.has(key) || sameLanguage.has(key)) {
                continue;
            }

            activeRequests += 1;
            requestTranslation(item.text)
                .then((data) => {
                    if (!Array.isArray(data)) {
                        return Promise.reject();
                    }
                    const detected = normalized(
                        typeof data[2] === "string" ? data[2] : "");
                    if (detected === normalized(targetLanguage)) {
                        sameLanguage.add(key);
                        return;
                    }
                    const translation = translatedText(data);
                    if (translation && translation !== item.text) {
                        cache.set(key, translation);
                        applyTranslation(item, translation);
                        pumpDelay = 200;
                    } else {
                        failed.add(key);
                    }
                })
                .catch(() => {
                    item.retries = (item.retries || 0) + 1;
                    if (item.retries <= 4) {
                        queuedNodes.add(item.node);
                        queue.push(item);
                        pumpDelay = Math.min(3000, pumpDelay * 2);
                    } else {
                        failed.add(key);
                    }
                })
                .finally(() => {
                    activeRequests -= 1;
                    window.setTimeout(pump, pumpDelay);
                });
        }
    };

    const queueNode = (node) => {
        if (queue.length >= MAX_QUEUE_LENGTH || queuedNodes.has(node) || isExcluded(node)) {
            return;
        }
        const text = (node.nodeValue || "").trim();
        if (!text || text.length > MAX_TEXT_LENGTH || !LETTER.test(text)) {
            return;
        }
        const key = targetLanguage + "|" + text;
        if (failed.has(key) || sameLanguage.has(key)) {
            return;
        }
        const cached = cache.get(key);
        if (cached) {
            applyTranslation({node, text}, cached);
            return;
        }
        // Mixed pages: even when the page-level verdict says "already target",
        // nodes written in a different script (Chinese on github.com) are
        // never the target language and always enter.
        const foreignScript = NONLATIN_LETTER.test(text);
        if (!foreignScript && pageCheckDone && pageIsTarget && targetIsLatin()) {
            sameLanguage.add(key);
            return;
        }
        queuedNodes.add(node);
        queue.push({node, text});
    };

    // One cheap detection request on a text sample decides whether the page is
    // already in the target language, so pages like an English article with an
    // English target cost a single round-trip instead of one per text node.
    const detectPageLanguage = (proceed) => {
        const sample = [];
        let total = 0;
        const walker = document.createTreeWalker(
            document.body || document.documentElement, NodeFilter.SHOW_TEXT);
        let node;
        while ((node = walker.nextNode()) && total < SAMPLE_LIMIT) {
            const text = (node.nodeValue || "").trim();
            if (text && !isExcluded(node) && LETTER.test(text)) {
                sample.push(text);
                total += text.length;
            }
        }
        if (!sample.length) {
            pageCheckDone = true;
            return proceed(false);
        }
        requestTranslation(sample.join(" ").slice(0, SAMPLE_LIMIT))
            .then((data) => {
                if (!Array.isArray(data)) {
                    return Promise.reject();
                }
                pageCheckDone = true;
                pageIsTarget = normalized(
                    typeof data[2] === "string" ? data[2] : "")
                    === normalized(targetLanguage);
                proceed(!pageIsTarget);
            })
            .catch(() => {
                pageCheckDone = true;
                proceed(true);
            });
    };

    const scan = (root = document.body) => {
        if (!root || !root.isConnected) {
            return;
        }
        const walk = () => {
            const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
            let node;
            while ((node = walker.nextNode())) {
                queueNode(node);
            }
            pump();
        };
        if (pageCheckDone) {
            walk();
            return;
        }
        detectPageLanguage(() => {
            walk();
        });
    };

    const scheduleScan = () => {
        window.clearTimeout(scanTimer);
        scanTimer = window.setTimeout(() => scan(), 350);
    };

    let autoEnabled = false;

    const observer = new MutationObserver(() => {
        if (autoEnabled) {
            scheduleScan();
        }
    });
    const observe = () => {
        observer.observe(document.body, {
            childList: true,
            characterData: true,
            subtree: true
        });
    };
    if (document.body) {
        observe();
    } else {
        document.addEventListener("DOMContentLoaded", observe, {once: true});
    }

    window.__speedyWatchPageTranslator = Object.freeze({
        configure: (options) => {
            if (options && typeof options.target === "string" && options.target) {
                const next = normalized(options.target) || options.target;
                if (next !== targetLanguage) {
                    targetLanguage = next;
                    pageCheckDone = false;
                    pageIsTarget = false;
                }
            }
            return targetLanguage;
        },
        setAuto: (enabled) => {
            autoEnabled = !!enabled;
        },
        translate: () => {
            autoEnabled = true;
            scheduleScan();
        },
        status: () => Object.freeze({
            target: targetLanguage,
            queued: queue.length,
            active: activeRequests,
            cached: cache.size,
            translated: translatedCount
        })
    });
    return "installed";
})();
