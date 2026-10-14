package com.jobhunter.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Text {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");
    private static final Pattern HTML_TAG = Pattern.compile("<[^>]+>");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private Text() {
    }

    public static String normalize(String value) {
        if (value == null) return "";
        String decomposed = Normalizer.normalize(value, Normalizer.Form.NFD);
        String withoutAccents = DIACRITICS.matcher(decomposed).replaceAll("");
        String lower = withoutAccents.toLowerCase(Locale.ROOT);
        return NON_ALPHANUMERIC.matcher(lower).replaceAll(" ").trim();
    }

    public static boolean containsTerm(String normalizedText, String term) {
        String normalizedTerm = normalize(term);
        if (normalizedTerm.isEmpty() || normalizedText == null || normalizedText.isEmpty()) return false;
        return (" " + normalizedText + " ").contains(" " + normalizedTerm + " ");
    }

    public static String cleanTitle(String value) {
        if (value == null) return null;
        return WHITESPACE.matcher(value).replaceAll(" ").trim();
    }

    public static String stripHtml(String html) {
        if (html == null) return null;
        String withoutTags = HTML_TAG.matcher(unescapeHtml(html)).replaceAll(" ");
        return WHITESPACE.matcher(unescapeHtml(withoutTags)).replaceAll(" ").trim();
    }

    public static String titleCase(String value) {
        if (value == null || value.isBlank()) return value;
        StringBuilder sb = new StringBuilder();
        for (String word : value.trim().split("\\s+")) {
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return sb.toString();
    }

    private static String unescapeHtml(String value) {
        return value.replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&");
    }
}
