package com.jobhunter.util;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

public final class Text {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
    private static final Pattern NON_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

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
}
