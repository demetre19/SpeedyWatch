package com.speedywatch.app;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import org.junit.Test;

public final class SavedSummaryChatTest {
    @Test
    public void buildSavedSummaryText_keepsSummaryUnchangedWithoutChat() {
        String summary = "# Summary\n\nOriginal Markdown.";

        assertEquals(
                summary,
                SavedSummaryChat.build(summary, Collections.emptyList())
        );
    }

    @Test
    public void buildSavedSummaryText_appendsEveryCompletedTurnInOrder() {
        String summary = "# Summary\n\nOriginal Markdown.";

        assertEquals(
                "# Summary\n\nOriginal Markdown."
                        + "\n\n---\n\n## Follow-up chat"
                        + "\n\n### You\n\nFirst question?"
                        + "\n\n### AI\n\nFirst answer."
                        + "\n\n### You\n\nSecond question?"
                        + "\n\n### AI\n\nSecond answer.",
                SavedSummaryChat.build(
                        summary,
                        Arrays.asList(
                                new SavedSummaryChat.Turn(
                                        "First question?",
                                        "First answer."
                                ),
                                new SavedSummaryChat.Turn(
                                        "Second question?",
                                        "Second answer."
                                )
                        )
                )
        );
    }
}
