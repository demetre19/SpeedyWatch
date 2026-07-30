package com.speedywatch.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

public final class MediaTranscriptEngineTest {
    @Test
    public void parseTimedText_keepsPackedSrtCueHeadersOutOfVisibleText() {
        String body = "1\n"
                + "00:00:01,000 --> 00:00:04,000\n"
                + "Add captions\n"
                + "2\n"
                + "00:00:04,045 --> 00:00:05,445\n"
                + "and subtitles to your videos.\n"
                + "3\n"
                + "00:00:05,875 --> 00:00:07,165\n"
                + "Enhancing accessibility\n";

        List<TranscriptEntry> entries = MediaTranscriptEngine.parseTimedText(body);

        assertEquals(3, entries.size());
        assertEquals(1.0, entries.get(0).startSeconds, 0.001);
        assertEquals("Add captions", entries.get(0).text);
        assertEquals("and subtitles to your videos.", entries.get(1).text);
        assertEquals("Enhancing accessibility", entries.get(2).text);
    }

    @Test
    public void parseTimedText_handlesWebVttSettingsAndMarkup() {
        String body = "WEBVTT\n\n"
                + "00:00:00.500 --> 00:00:02.000 align:start position:0%\n"
                + "<b>Hello</b> &amp; welcome\n\n"
                + "00:02.000 --> 00:04.250\n"
                + "Second line\n";

        List<TranscriptEntry> entries = MediaTranscriptEngine.parseTimedText(body);

        assertEquals(2, entries.size());
        assertEquals("Hello & welcome", entries.get(0).text);
        assertEquals(2.0, entries.get(1).startSeconds, 0.001);
        assertEquals("Second line", entries.get(1).text);
    }
}
