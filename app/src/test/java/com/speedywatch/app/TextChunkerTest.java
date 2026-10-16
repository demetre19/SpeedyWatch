package com.speedywatch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class TextChunkerTest {

    @Test
    public void split_coversOriginalVerbatim() {
        String source = join("",
                "First sentence here. Second sentence follows right away. ",
                "Third sentence completes the thought with more detail. ",
                "Fourth and final sentence closes the block of text.");
        List<String> chunks = TextChunker.split(source, 80);
        assertTrue(chunks.size() > 1);
        assertTrue(TextChunker.covers(source, chunks));
        for (String chunk : chunks) {
            assertTrue(TextChunker.weightedLength(chunk) <= 80);
            assertEquals(chunk, chunk.trim());
        }
    }

    @Test
    public void split_prefersSentenceBoundaries() {
        String source = "Aaa bbb ccc. Ddd eee fff ggg hhh iii jjj kkk lll mmm.";
        List<String> chunks = TextChunker.split(source, 30);
        assertEquals("Aaa bbb ccc.", chunks.get(0));
        assertTrue(TextChunker.covers(source, chunks));
    }

    @Test
    public void split_prefersParagraphThenWordBoundary() {
        String source = "alpha beta gamma\n\ndelta epsilon zeta eta theta iota";
        List<String> chunks = TextChunker.split(source, 25);
        assertEquals("alpha beta gamma", chunks.get(0));
        assertTrue(TextChunker.covers(source, chunks));
    }

    @Test
    public void split_hardSplitsUnbreakableRun() {
        String source = join("", Collections.nCopies(500, "a").toArray(new String[0]));
        List<String> chunks = TextChunker.split(source, 120);
        assertTrue(chunks.size() >= 4);
        assertTrue(TextChunker.covers(source, chunks));
        for (String chunk : chunks) {
            assertTrue(TextChunker.weightedLength(chunk) <= 120);
        }
    }
    @Test
    public void split_neverBreaksInsideUrl() {
        String url = "https://example.com/a/really/long/path/segment";
        String source = "Intro words here. " + url + " trailing words after the link.";
        List<String> chunks = TextChunker.split(source, 60);
        String joined = chunks.stream().collect(Collectors.joining("\n"));
        assertTrue(joined.contains(url));
        assertTrue(TextChunker.covers(source, chunks));

        String urlCrossing = "Intro words fill the start. "
                + url + " trailing words after the link.";
        List<String> chunks2 = TextChunker.split(urlCrossing, 45);
        assertTrue(chunks2.stream().collect(Collectors.joining("\n"))
                .contains(url));
        assertTrue(TextChunker.covers(urlCrossing, chunks2));
    }

    @Test
    public void weightedLength_countsUrlsAsTwentyThree() {
        String url = "https://example.com/some/endpoint/path";
        assertEquals(23, TextChunker.weightedLength(url));
        assertEquals(23 + 5, TextChunker.weightedLength("See: " + url));
    }

    @Test
    public void weightedLength_countsCjkAsTwo() {
        assertEquals(6, TextChunker.weightedLength("日本語"));
        assertEquals(2, TextChunker.weightedLength("😀"));
        assertEquals(5, TextChunker.weightedLength("hello"));
    }

    @Test
    public void bodyLimit_reservesNumberingSpace() {
        String source = join(" ", Collections.nCopies(200, "word").toArray(new String[0]));
        int body = TextChunker.bodyLimit(source, 280, true);
        List<String> numbered = TextChunker.numbered(TextChunker.split(source, body));
        for (String post : numbered) {
            assertTrue(TextChunker.weightedLength(post) <= 280);
        }
    }

    @Test
    public void numbered_prefixesSequentially() {
        List<String> out = TextChunker.numbered(Arrays.asList("one", "two", "three"));
        assertEquals("1/3 one", out.get(0));
        assertEquals("3/3 three", out.get(2));
    }

    @Test
    public void parseAiChunks_acceptsCleanArray() {
        List<String> parsed = TextChunker.parseAiChunks(
                "Here you go:\n[\"one chunk.\", \"two \\\"quoted\\\" chunk.\"]");
        assertNotNull(parsed);
        assertEquals(Arrays.asList("one chunk.", "two \"quoted\" chunk."), parsed);
    }

    @Test
    public void parseAiChunks_stripsNumberingPrefixes() {
        List<String> parsed = TextChunker.parseAiChunks("[\"1/2 alpha\", \"2/2 beta\"]");
        assertNotNull(parsed);
        assertEquals(Arrays.asList("alpha", "beta"), parsed);
    }

    @Test
    public void parseAiChunks_rejectsMalformedOrEmpty() {
        assertNull(TextChunker.parseAiChunks(null));
        assertNull(TextChunker.parseAiChunks("no array here"));
        assertNull(TextChunker.parseAiChunks("[]"));
        assertNull(TextChunker.parseAiChunks("[1, 2, 3]"));
        assertNull(TextChunker.parseAiChunks("[\"unterminated"));
    }

    @Test
    public void acceptable_rejectsParaphrasedOrMissingText() {
        String source = "The quick brown fox jumps over the lazy dog.";
        assertFalse(TextChunker.acceptable(
                source,
                Collections.singletonList("A fast fox jumped a dog."),
                280
        ));
        assertFalse(TextChunker.acceptable(
                source,
                Arrays.asList("The quick brown fox", "jumps somewhere else entirely"),
                280
        ));
        assertFalse(TextChunker.acceptable(source, Collections.emptyList(), 280));
        assertTrue(TextChunker.acceptable(
                source,
                Arrays.asList("The quick brown fox", "jumps over the lazy dog."),
                280
        ));
    }

    @Test
    public void covers_ignoresDroppedBoundaryWhitespace() {
        String source = "alpha beta   gamma\n\ndelta";
        assertTrue(TextChunker.covers(
                source,
                Arrays.asList("alpha beta", "gamma", "delta")
        ));
        assertFalse(TextChunker.covers(
                source,
                Arrays.asList("alpha beta", "delta")
        ));
    }

    private static String join(String separator, String... parts) {
        return String.join(separator, parts);
    }
}
