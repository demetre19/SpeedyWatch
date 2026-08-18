package com.speedywatch.app;

import java.util.List;

/** Formats completed summary follow-up turns for durable saved content. */
final class SavedSummaryChat {
    private SavedSummaryChat() {
    }

    static String build(String summaryText, List<Turn> turns) {
        if (turns.isEmpty()) {
            return summaryText;
        }
        StringBuilder savedText = new StringBuilder(summaryText);
        savedText.append("\n\n---\n\n## Follow-up chat");
        for (Turn turn : turns) {
            savedText.append("\n\n### You\n\n")
                    .append(turn.question)
                    .append("\n\n### AI\n\n")
                    .append(turn.answer);
        }
        return savedText.toString();
    }

    static final class Turn {
        final String question;
        final String answer;

        Turn(String question, String answer) {
            this.question = question;
            this.answer = answer;
        }
    }
}
