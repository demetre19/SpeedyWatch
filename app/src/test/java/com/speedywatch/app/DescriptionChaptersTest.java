package com.speedywatch.app;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public final class DescriptionChaptersTest {
    private static final String DESCRIPTION = """
            Useful links above the chapters.

            0:00 Introduction
            2:35 - Installation
            7:20 Configuration
            12:45 Conclusion
            """;

    @Test
    public void parse_acceptsYouTubeDescriptionChapterFormat() {
        List<DescriptionChapters.Chapter> chapters = DescriptionChapters.parse(DESCRIPTION, 900);

        assertEquals(4, chapters.size());
        assertEquals(0, chapters.get(0).startSeconds, 0);
        assertEquals("Introduction", chapters.get(0).title);
        assertEquals(155, chapters.get(1).startSeconds, 0);
        assertEquals("Installation", chapters.get(1).title);
        assertEquals(765, chapters.get(3).startSeconds, 0);
    }

    @Test
    public void parse_rejectsTextThatDoesNotMeetYouTubeChapterRules() {
        assertTrue(DescriptionChapters.parse("1:00 One\n2:00 Two\n3:00 Three", 300).isEmpty());
        assertTrue(DescriptionChapters.parse("0:00 One\n0:05 Two\n0:20 Three", 300).isEmpty());
        assertTrue(DescriptionChapters.parse("0:00 One\n1:00 Two", 300).isEmpty());
        assertTrue(DescriptionChapters.parse("0:00 One\n1:00 Two\n4:55 Three", 300).isEmpty());
    }

    @Test
    public void target_movesForwardAndRestartsOrRewindsBackward() {
        List<DescriptionChapters.Chapter> chapters = DescriptionChapters.parse(DESCRIPTION, 900);

        assertEquals("Installation", DescriptionChapters.target(chapters, 20, true).title);
        assertEquals("Configuration", DescriptionChapters.target(chapters, 160, true).title);
        assertEquals("Installation", DescriptionChapters.target(chapters, 200, false).title);
        assertEquals("Introduction", DescriptionChapters.target(chapters, 156, false).title);
        assertNull(DescriptionChapters.target(chapters, 800, true));
        assertNull(DescriptionChapters.target(chapters, 0, false));
    }
    @Test
    public void swipe_policy_usesTopAndMiddleAndMapsRightToNext() {
        assertTrue(DescriptionChapters.swipeStartsInActiveRegion(true, 0, 2_000));
        assertTrue(DescriptionChapters.swipeStartsInActiveRegion(true, 1_599, 2_000));
        assertFalse(DescriptionChapters.swipeStartsInActiveRegion(true, 1_600, 2_000));
        assertFalse(DescriptionChapters.swipeStartsInActiveRegion(false, 500, 2_000));

        assertEquals(1, DescriptionChapters.swipeDirection(100, 10, 72));
        assertEquals(-1, DescriptionChapters.swipeDirection(-100, 10, 72));
        assertEquals(0, DescriptionChapters.swipeDirection(60, 0, 72));
        assertEquals(0, DescriptionChapters.swipeDirection(100, 80, 72));
    }


    @Test
    public void parse_supportsHourTimestampsAndBulletedLines() {
        String description = "- 0:00 Start\n* 1:02:03 Deep dive\n> 1:20:00 Finish";

        List<DescriptionChapters.Chapter> chapters = DescriptionChapters.parse(description, 5_000);

        assertEquals(3, chapters.size());
        assertEquals(3_723, chapters.get(1).startSeconds, 0);
    }
}
