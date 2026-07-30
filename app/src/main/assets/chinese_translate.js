(() => {
    "use strict";

    const existing = window.__speedyWatchChineseTranslator;
    if (existing) {
        existing.scan();
        return "reused";
    }

    const CHINESE = /[\u3400-\u4DBF\u4E00-\u9FFF\uF900-\uFAFF]/;
    const MAX_TEXT_LENGTH = 500;
    const MAX_QUEUE_LENGTH = 200;
    const cache = new Map();
    const failed = new Set();
    const queuedNodes = new WeakSet();
    const queue = [];
    let activeRequests = 0;
    let scanTimer = 0;
    let translatedCount = 0;

    const isExcluded = (node) => {
        const parent = node && node.parentElement;
        if (!parent || !parent.isConnected) {
            return true;
        }
        if (parent.closest(
            "script, style, noscript, template, input, textarea, select, option, " +
            "[contenteditable=''], [contenteditable='true'], [hidden], [aria-hidden='true']"
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

    const pump = () => {
        while (activeRequests < 3 && queue.length > 0) {
            const item = queue.shift();
            queuedNodes.delete(item.node);
            if (!item.node.isConnected || isExcluded(item.node)) {
                continue;
            }
            const cached = cache.get(item.text);
            if (cached) {
                applyTranslation(item, cached);
                continue;
            }
            if (failed.has(item.text)) {
                continue;
            }

            activeRequests += 1;
            const endpoint = "https://translate.googleapis.com/translate_a/single" +
                "?client=gtx&sl=zh&tl=en&dt=t&q=" + encodeURIComponent(item.text);
            fetch(endpoint, {
                method: "GET",
                credentials: "omit",
                referrerPolicy: "no-referrer",
                cache: "force-cache"
            })
                .then((response) => response.ok ? response.json() : Promise.reject())
                .then((data) => {
                    const translation = translatedText(data);
                    if (translation && translation !== item.text) {
                        cache.set(item.text, translation);
                        applyTranslation(item, translation);
                    } else {
                        failed.add(item.text);
                    }
                })
                .catch(() => failed.add(item.text))
                .finally(() => {
                    activeRequests -= 1;
                    window.setTimeout(pump, 120);
                });
        }
    };

    const queueNode = (node) => {
        if (queue.length >= MAX_QUEUE_LENGTH || queuedNodes.has(node) || isExcluded(node)) {
            return;
        }
        const text = (node.nodeValue || "").trim();
        if (!text || text.length > MAX_TEXT_LENGTH || !CHINESE.test(text) || failed.has(text)) {
            return;
        }
        const cached = cache.get(text);
        if (cached) {
            applyTranslation({node, text}, cached);
            return;
        }
        queuedNodes.add(node);
        queue.push({node, text});
    };

    const scan = (root = document.body) => {
        if (!root || !root.isConnected) {
            return;
        }
        const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
        let node;
        while ((node = walker.nextNode())) {
            queueNode(node);
        }
        pump();
    };

    const scheduleScan = () => {
        window.clearTimeout(scanTimer);
        scanTimer = window.setTimeout(() => scan(), 350);
    };

    const observer = new MutationObserver(scheduleScan);
    if (document.body) {
        observer.observe(document.body, {
            childList: true,
            characterData: true,
            subtree: true
        });
        scan();
    } else {
        document.addEventListener("DOMContentLoaded", () => {
            observer.observe(document.body, {
                childList: true,
                characterData: true,
                subtree: true
            });
            scan();
        }, {once: true});
    }

    window.__speedyWatchChineseTranslator = Object.freeze({
        scan: scheduleScan,
        status: () => Object.freeze({
            queued: queue.length,
            active: activeRequests,
            cached: cache.size,
            translated: translatedCount
        })
    });
    return "installed";
})();
