package com.speedywatch.app;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Splits arbitrary text into sequential, verbatim, post-sized chunks.
 * Counting approximates X's weighted limit: ASCII/Latin characters weigh 1,
 * CJK and non-BMP characters weigh 2, and any URL token weighs 23.
 */
final class TextChunker {
    static final int DEFAULT_LIMIT = 280;
    static final int MIN_LIMIT = 50;
    static final int MAX_LIMIT = 5000;
    static final int AI_MAX_INPUT_CHARS = 8000;

    private static final int URL_WEIGHT = 23;
    private static final Pattern NUMBER_PREFIX =
            Pattern.compile("^\\s*\\d{1,5}\\s*/\\s*\\d{1,5}[\\s:.\\u2013\\u2014-]*");
    private static final String SENTENCE_ENDERS = ".!?…。！？";
    private static final String CLOSERS = "\"”’')]}›»";

    private TextChunker() {
    }

    static int boundLimit(int limit) {
        return Math.max(MIN_LIMIT, Math.min(MAX_LIMIT, limit));
    }

    /** Weighted length of the whole string. */
    static int weightedLength(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        int[] prefix = prefixWeights(text);
        return prefix[text.length()];
    }

    /**
     * Maximum weighted length for each chunk body so that an "i/n " prefix
     * keeps the finished post within {@code limit}.
     */
    static int bodyLimit(String text, int limit, boolean numbering) {
        if (!numbering) {
            return limit;
        }
        int reserve = numberingReserve(split(text, limit).size());
        int body = Math.max(1, limit - reserve);
        int grown = numberingReserve(split(text, body).size());
        return grown > reserve ? Math.max(1, limit - grown) : body;
    }

    static int numberingReserve(int chunkCount) {
        int digits = String.valueOf(Math.max(1, chunkCount)).length();
        return digits * 2 + 2;
    }

    static List<String> numbered(List<String> bodies) {
        List<String> out = new ArrayList<>(bodies.size());
        int total = bodies.size();
        for (int index = 0; index < total; index++) {
            out.add((index + 1) + "/" + total + " " + bodies.get(index));
        }
        return out;
    }

    /**
     * Deterministic verbatim split. Breaks at the latest sentence end inside
     * the limit, then the latest newline boundary, then the latest word
     * boundary, and only hard-splits unbreakable runs. Leading and trailing
     * whitespace is dropped per chunk; interior characters are never altered.
     */
    static List<String> split(String text, int limit) {
        List<String> chunks = new ArrayList<>();
        if (text == null) {
            return chunks;
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        int length = normalized.length();
        int[] prefix = prefixWeights(normalized);
        int pos = 0;
        while (pos < length) {
            while (pos < length && Character.isWhitespace(normalized.charAt(pos))) {
                pos++;
            }
            if (pos >= length) {
                break;
            }
            int end = rstrip(normalized, pos, length);
            if (prefix[end] - prefix[pos] <= limit) {
                chunks.add(normalized.substring(pos, end));
                break;
            }
            int hardEnd = cutoff(prefix, pos, limit);
            int bodyEnd = pickBreak(normalized, pos, hardEnd);
            if (bodyEnd <= pos) {
                bodyEnd = protectUrl(normalized, pos, hardEnd);
            }
            if (bodyEnd <= pos) {
                bodyEnd = Math.max(pos + 1, hardEnd);
            }
            chunks.add(normalized.substring(pos, bodyEnd));
            pos = bodyEnd;
        }
        return chunks;
    }

    /**
     * True when the chunks reproduce the original exactly, ignoring any
     * whitespace that was dropped at chunk boundaries.
     */
    static boolean covers(String original, List<String> bodies) {
        if (original == null || bodies == null || bodies.isEmpty()) {
            return false;
        }
        StringBuilder joined = new StringBuilder();
        for (String body : bodies) {
            joined.append(stripWhitespace(body));
        }
        return stripWhitespace(original).contentEquals(joined);
    }

    /**
     * Parses an AI reply of the form {@code ["chunk", "chunk", ...]} without
     * org.json so the parser stays unit-testable off-device.
     */
    static List<String> parseAiChunks(String raw) {
        if (raw == null) {
            return null;
        }
        int start = raw.indexOf('[');
        int end = raw.lastIndexOf(']');
        if (start < 0 || end <= start) {
            return null;
        }
        List<String> items = parseJsonStringArray(raw, start, end);
        if (items == null || items.isEmpty() || items.size() > 5000) {
            return null;
        }
        List<String> out = new ArrayList<>(items.size());
        for (String item : items) {
            String cleaned = NUMBER_PREFIX.matcher(item).replaceFirst("").trim();
            if (cleaned.isEmpty()) {
                return null;
            }
            out.add(cleaned);
        }
        return out;
    }

    /** Parses a JSON array containing only string elements. */
    private static List<String> parseJsonStringArray(String text, int start, int end) {
        List<String> items = new ArrayList<>();
        int index = start + 1;
        while (index < end) {
            char c = text.charAt(index);
            if (Character.isWhitespace(c) || c == ',') {
                index++;
                continue;
            }
            if (c != '"') {
                return null;
            }
            StringBuilder value = new StringBuilder();
            index++;
            while (index < end) {
                char d = text.charAt(index);
                if (d == '"') {
                    break;
                }
                if (d == '\\') {
                    index++;
                    if (index >= end) {
                        return null;
                    }
                    char escaped = text.charAt(index);
                    switch (escaped) {
                        case '"': value.append('"'); break;
                        case '\\': value.append('\\'); break;
                        case '/': value.append('/'); break;
                        case 'b': value.append('\b'); break;
                        case 'f': value.append('\f'); break;
                        case 'n': value.append('\n'); break;
                        case 'r': value.append('\r'); break;
                        case 't': value.append('\t'); break;
                        case 'u':
                            if (index + 4 >= end) {
                                return null;
                            }
                            try {
                                value.append((char) Integer.parseInt(
                                        text.substring(index + 1, index + 5), 16));
                            } catch (NumberFormatException error) {
                                return null;
                            }
                            index += 4;
                            break;
                        default:
                            return null;
                    }
                    index++;
                    continue;
                }
                value.append(d);
                index++;
            }
            if (index >= end || text.charAt(index) != '"') {
                return null;
            }
            items.add(value.toString());
            index++;
        }
        return items;
    }

    /** AI chunks are usable only when verbatim and every chunk fits. */
    static boolean acceptable(String original, List<String> bodies, int bodyLimit) {
        if (!covers(original, bodies)) {
            return false;
        }
        for (String body : bodies) {
            if (weightedLength(body) > bodyLimit) {
                return false;
            }
        }
        return true;
    }

    private static String stripWhitespace(String text) {
        StringBuilder out = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            if (!Character.isWhitespace(c)) {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** Largest end index such that weight(text[pos, end)) <= limit. */
    private static int cutoff(int[] prefix, int pos, int limit) {
        int allowed = prefix[pos] + limit;
        int low = pos;
        int high = prefix.length - 1;
        while (low < high) {
            int mid = (low + high + 1) >>> 1;
            if (prefix[mid] <= allowed) {
                low = mid;
            } else {
                high = mid - 1;
            }
        }
        return low;
    }

    /**
     * Picks the best body end inside the window: the latest sentence end,
     * then the latest newline boundary, then the latest word boundary.
     * Returns <= pos when the window has no natural boundary.
     */
    private static int pickBreak(String text, int pos, int hardEnd) {
        int sentenceEnd = -1;
        int newlineEnd = -1;
        int wordEnd = -1;
        int lastNonWhitespace = -1;
        int index = pos;
        while (index < hardEnd) {
            char c = text.charAt(index);
            if (Character.isWhitespace(c)) {
                index++;
                continue;
            }
            int urlEnd = urlTokenEnd(text, index);
            if (urlEnd > index) {
                // A URL token is atomic: never score break candidates inside it.
                lastNonWhitespace = urlEnd - 1;
                index = urlEnd;
                continue;
            }
            if (lastNonWhitespace >= pos) {
                int bodyEnd = lastNonWhitespace + 1;
                wordEnd = bodyEnd;
                if (isSentenceEnd(text, lastNonWhitespace)) {
                    sentenceEnd = bodyEnd;
                } else {
                    for (int k = lastNonWhitespace + 1; k < index; k++) {
                        if (text.charAt(k) == '\n') {
                            newlineEnd = bodyEnd;
                            break;
                        }
                    }
                }
            }
            lastNonWhitespace = index;
            index++;
        }
        if (sentenceEnd > pos) {
            return sentenceEnd;
        }
        if (newlineEnd > pos) {
            return newlineEnd;
        }
        if (wordEnd > pos) {
            return wordEnd;
        }
        return -1;
    }

    /** Never hard-split inside a URL token; back off to its start. */
    private static int protectUrl(String text, int pos, int hardEnd) {
        int start = hardEnd;
        while (start > pos && !Character.isWhitespace(text.charAt(start - 1))) {
            start--;
        }
        if (start <= pos || start == hardEnd) {
            return hardEnd;
        }
        int end = start;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        return end > hardEnd && isUrlToken(text, start) ? start : hardEnd;
    }

    private static boolean isUrlToken(String text, int start) {
        return text.regionMatches(true, start, "https://", 0, 8)
                || text.regionMatches(true, start, "http://", 0, 7)
                || text.regionMatches(true, start, "www.", 0, 4);
    }

    private static boolean isSentenceEnd(String text, int index) {
        int probe = index;
        while (probe >= 0 && CLOSERS.indexOf(text.charAt(probe)) >= 0) {
            probe--;
        }
        return probe >= 0 && SENTENCE_ENDERS.indexOf(text.charAt(probe)) >= 0;
    }

    private static int rstrip(String text, int from, int to) {
        int end = to;
        while (end > from && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        return end;
    }

    /** Per-position cumulative weights; flat inside URL tokens. */
    private static int[] prefixWeights(String text) {
        int length = text.length();
        int[] prefix = new int[length + 1];
        int index = 0;
        while (index < length) {
            int urlEnd = urlTokenEnd(text, index);
            if (urlEnd > index) {
                for (int j = index; j < urlEnd; j++) {
                    prefix[j + 1] = prefix[index];
                }
                prefix[urlEnd] = prefix[index] + URL_WEIGHT;
                index = urlEnd;
                continue;
            }
            int codePoint = text.codePointAt(index);
            int weight = charWeight(codePoint);
            int span = Character.charCount(codePoint);
            for (int j = index; j < index + span; j++) {
                prefix[j + 1] = prefix[index] + weight;
            }
            index += span;
        }
        return prefix;
    }

    private static int urlTokenEnd(String text, int index) {
        if (index > 0 && !isUrlBoundary(text.charAt(index - 1))) {
            return -1;
        }
        if (!isUrlToken(text, index)) {
            return -1;
        }
        int end = index;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        return end;
    }

    private static boolean isUrlBoundary(char c) {
        return Character.isWhitespace(c)
                || c == '(' || c == '[' || c == '<' || c == '"' || c == '\'';
    }

    private static int charWeight(int codePoint) {
        if (codePoint <= 0x10FF) {
            return 1;
        }
        if (codePoint >= 0x2000 && codePoint <= 0x200D) {
            return 1;
        }
        if (codePoint >= 0x2010 && codePoint <= 0x201F) {
            return 1;
        }
        if (codePoint >= 0x2032 && codePoint <= 0x2037) {
            return 1;
        }
        return 2;
    }
}
