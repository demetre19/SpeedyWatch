package com.speedywatch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public final class WatchPathPlanTest {
    private static final String SOURCE = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    @Test
    public void parse_acceptsBoundedChronologicalRouteAndComputesSkippedRanges() {
        WatchPathPlan plan = WatchPathPlan.parse(
                "WATCHPATH 1\n"
                        + "SEGMENT | 10 | 70 | Context | Establishes the problem\n"
                        + "SEGMENT | 100 | 160 | Steps | Shows the implementation",
                SOURCE,
                "Learn the implementation",
                5,
                200
        );

        assertEquals(2, plan.segments.size());
        assertEquals(120, plan.selectedDurationSeconds(), 0.001);
        assertEquals(3, plan.skippedRanges().size());
        assertEquals(0, plan.skippedRanges().get(0).startSeconds, 0.001);
        assertEquals(10, plan.skippedRanges().get(0).endSeconds, 0.001);
        assertEquals(70, plan.skippedRanges().get(1).startSeconds, 0.001);
        assertEquals(100, plan.skippedRanges().get(1).endSeconds, 0.001);
        assertEquals(160, plan.skippedRanges().get(2).startSeconds, 0.001);
        assertEquals(200, plan.skippedRanges().get(2).endSeconds, 0.001);
    }

    @Test
    public void parse_toleratesOneCodeFenceWithoutWeakeningSchema() {
        WatchPathPlan plan = WatchPathPlan.parse(
                "```text\nWATCHPATH 1\nSEGMENT | 0 | 60 | Intro | Required context\n```",
                SOURCE,
                "Understand the context",
                5,
                120
        );

        assertEquals("Intro", plan.segments.get(0).title);
    }

    @Test
    public void parse_rejectsOverlapBudgetOverflowAndOutOfRangeSegments() {
        assertInvalid(() -> WatchPathPlan.parse(
                "WATCHPATH 1\n"
                        + "SEGMENT | 0 | 100 | First | First\n"
                        + "SEGMENT | 90 | 120 | Second | Second",
                SOURCE,
                "Goal",
                5,
                200
        ));
        assertInvalid(() -> WatchPathPlan.parse(
                "WATCHPATH 1\nSEGMENT | 0 | 301 | Too long | Exceeds budget",
                SOURCE,
                "Goal",
                5,
                400
        ));
        assertInvalid(() -> WatchPathPlan.parse(
                "WATCHPATH 1\nSEGMENT | 0 | 122 | Too far | Exceeds transcript",
                SOURCE,
                "Goal",
                5,
                120
        ));
    }

    @Test
    public void parse_rejectsInvalidSchemaAndMoreThanTwentySegments() {
        assertInvalid(() -> WatchPathPlan.parse(
                "SEGMENT | 0 | 30 | Missing header | Invalid",
                SOURCE,
                "Goal",
                5,
                60
        ));

        StringBuilder response = new StringBuilder("WATCHPATH 1\n");
        for (int index = 0; index < 21; index++) {
            response.append("SEGMENT | ")
                    .append(index * 2)
                    .append(" | ")
                    .append(index * 2 + 1)
                    .append(" | Part ")
                    .append(index)
                    .append(" | Reason\n");
        }
        assertInvalid(() -> WatchPathPlan.parse(
                response.toString(),
                SOURCE,
                "Goal",
                5,
                60
        ));
    }

    @Test
    public void userMessage_containsOnlyNeutralRequestDataWithNumericCueRanges() {
        List<TranscriptEntry> entries = new ArrayList<>();
        entries.add(new TranscriptEntry(12.5, 3.25, "First cue"));
        entries.add(new TranscriptEntry(20, Double.NaN, "Second cue"));

        String message = WatchPathPlan.buildUserMessage(
                "YouTube captions",
                "Example video",
                SOURCE,
                "Find the implementation",
                10,
                entries
        );

        assertTrue(message.contains("Viewing goal: Find the implementation"));
        assertTrue(message.contains("Time budget minutes: 10"));
        assertTrue(message.contains("startSeconds=12.500 endSeconds=15.750"));
        assertTrue(message.contains("startSeconds=20.000 endSeconds=21.000"));
        assertFalse(message.contains("WATCHPATH 1"));
        assertFalse(message.contains("SEGMENT |"));
    }

    private static void assertInvalid(Runnable action) {
        try {
            action.run();
            fail("Expected invalid WatchPath data to be rejected");
        } catch (IllegalArgumentException expected) {
            // Expected.
        }
    }
}
