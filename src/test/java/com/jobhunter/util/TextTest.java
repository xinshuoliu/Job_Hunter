package com.jobhunter.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextTest {

    @Test
    @DisplayName("stripHtml removes tags and decodes escaped markup")
    void stripsHtml() {
        assertEquals("Hello & welcome to the team", Text.stripHtml("<p>Hello &amp; <b>welcome</b></p>\n<p>to the&nbsp;team</p>"));
        assertEquals("Title", Text.stripHtml("&lt;h1&gt;Title&lt;/h1&gt;"));
        assertNull(Text.stripHtml(null));
    }

    @Test
    @DisplayName("cleanTitle collapses whitespace")
    void cleansTitles() {
        assertEquals("Software Intern - Winter 2027", Text.cleanTitle("  Software   Intern -\tWinter 2027 "));
    }

    @Test
    @DisplayName("titleCase capitalises each word")
    void titleCases() {
        assertEquals("Valence Labs", Text.titleCase("valence labs"));
        assertEquals("AppDirect", Text.titleCase("appDirect"));
    }

    @Test
    @DisplayName("containsTerm matches whole normalized words")
    void containsTerm() {
        String text = Text.normalize("Stagiaire développeur / Co-op Intern");
        assertTrue(Text.containsTerm(text, "développeur"));
        assertTrue(Text.containsTerm(text, "co-op"));
        assertFalse(Text.containsTerm(text, "dev"));
        assertFalse(Text.containsTerm(text, ""));
    }
}
