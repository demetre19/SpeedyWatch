package com.speedywatch.app;

import android.graphics.Typeface;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.style.BulletSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.text.style.URLSpan;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MarkdownRenderer {
    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.+)$");
    private static final Pattern BULLET = Pattern.compile("^(\\s*)[-+*]\\s+(.+)$");
    private static final Pattern NUMBERED = Pattern.compile("^(\\s*)(\\d+)[.)]\\s+(.+)$");
    private static final Pattern RULE = Pattern.compile("^\\s*([-*_])(?:\\s*\\1){2,}\\s*$");

    private MarkdownRenderer() {
    }

    static CharSequence render(String markdown, float density) {
        SpannableStringBuilder output = new SpannableStringBuilder();
        String normalized = markdown == null ? "" : markdown.replace("\r\n", "\n").replace('\r', '\n');
        String[] lines = normalized.split("\n", -1);

        for (int index = 0; index < lines.length; index++) {
            appendLine(output, lines[index], density);
            if (index < lines.length - 1) {
                output.append('\n');
            }
        }
        return output;
    }

    private static void appendLine(SpannableStringBuilder output, String line, float density) {
        Matcher heading = HEADING.matcher(line);
        if (heading.matches()) {
            int start = output.length();
            appendInline(output, heading.group(2));
            int end = output.length();
            output.setSpan(new StyleSpan(Typeface.BOLD), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            output.setSpan(
                    new RelativeSizeSpan(heading.group(1).length() == 1 ? 1.35f : 1.18f),
                    start,
                    end,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            return;
        }

        Matcher bullet = BULLET.matcher(line);
        if (bullet.matches()) {
            int start = output.length();
            appendInline(output, bullet.group(2));
            int indent = bullet.group(1).length() / 2;
            int gap = Math.max(6, Math.round((8 + indent * 8) * density));
            output.setSpan(new BulletSpan(gap), start, output.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            return;
        }

        Matcher numbered = NUMBERED.matcher(line);
        if (numbered.matches()) {
            int start = output.length();
            output.append(numbered.group(2)).append(". ");
            appendInline(output, numbered.group(3));
            int indent = numbered.group(1).length() / 2;
            int margin = Math.max(12, Math.round((16 + indent * 8) * density));
            output.setSpan(
                    new LeadingMarginSpan.Standard(margin),
                    start,
                    output.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            return;
        }

        if (!RULE.matcher(line).matches()) {
            appendInline(output, line);
        }
    }

    private static void appendInline(SpannableStringBuilder output, String text) {
        int index = 0;
        while (index < text.length()) {
            if (text.startsWith("**", index) || text.startsWith("__", index)) {
                String marker = text.substring(index, index + 2);
                int closing = text.indexOf(marker, index + 2);
                if (closing > index + 2) {
                    int start = output.length();
                    appendInline(output, text.substring(index + 2, closing));
                    output.setSpan(
                            new StyleSpan(Typeface.BOLD),
                            start,
                            output.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                    index = closing + 2;
                    continue;
                }
            }

            char current = text.charAt(index);
            if (current == '`') {
                int closing = text.indexOf('`', index + 1);
                if (closing > index + 1) {
                    int start = output.length();
                    output.append(text, index + 1, closing);
                    output.setSpan(
                            new TypefaceSpan("monospace"),
                            start,
                            output.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                    index = closing + 1;
                    continue;
                }
            }

            if (current == '[') {
                int labelEnd = text.indexOf("](", index + 1);
                int urlEnd = labelEnd < 0 ? -1 : text.indexOf(')', labelEnd + 2);
                if (labelEnd > index + 1 && urlEnd > labelEnd + 2) {
                    int start = output.length();
                    appendInline(output, text.substring(index + 1, labelEnd));
                    output.setSpan(
                            new URLSpan(text.substring(labelEnd + 2, urlEnd)),
                            start,
                            output.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                    index = urlEnd + 1;
                    continue;
                }
            }

            if (current == '*' || current == '_') {
                int closing = text.indexOf(current, index + 1);
                if (closing > index + 1) {
                    int start = output.length();
                    appendInline(output, text.substring(index + 1, closing));
                    output.setSpan(
                            new StyleSpan(Typeface.ITALIC),
                            start,
                            output.length(),
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    );
                    index = closing + 1;
                    continue;
                }
            }

            output.append(current);
            index++;
        }
    }
}
