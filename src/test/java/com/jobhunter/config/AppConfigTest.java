package com.jobhunter.config;

import com.jobhunter.model.FilterCriteria.ExcludeScope;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

    private static final String UNSET = "JOBHUNTER_TEST_VARIABLE_THAT_IS_NEVER_SET";

    @Test
    @DisplayName("Values without placeholders are returned unchanged")
    void plainValuesPassThrough() {
        assertEquals("smtp.gmail.com", AppConfig.resolve("smtp.gmail.com"));
        assertNull(AppConfig.resolve(null));
    }

    @Test
    @DisplayName("A placeholder is replaced with the environment variable")
    void placeholderReadsEnvironment() {
        String path = System.getenv("PATH");
        assumeEnvironmentHasPath(path);

        assertEquals(path, AppConfig.resolve("${PATH}"));
    }

    @Test
    @DisplayName("An unset variable falls back to the inline default")
    void unsetVariableUsesDefault() {
        assertEquals("fallback", AppConfig.resolve("${" + UNSET + ":-fallback}"));
    }

    @Test
    @DisplayName("An unset variable with no default resolves to an empty value")
    void unsetVariableWithoutDefaultIsEmpty() {
        assertEquals("", AppConfig.resolve("${" + UNSET + "}"));
    }

    @Test
    @DisplayName("Placeholders are substituted inside surrounding text")
    void placeholderInsideText() {
        assertEquals("prefix-value-suffix",
                AppConfig.resolve("prefix-${" + UNSET + ":-value}-suffix"));
    }

    @Test
    @DisplayName("exclude_scope parses known values and falls back to title")
    void parsesExcludeScope() {
        assertEquals(ExcludeScope.ALL, AppConfig.parseExcludeScope("all"));
        assertEquals(ExcludeScope.TITLE, AppConfig.parseExcludeScope(" Title "));
        assertEquals(ExcludeScope.TITLE, AppConfig.parseExcludeScope("description"));
    }

    private void assumeEnvironmentHasPath(String path) {
        if (path == null || path.isEmpty()) {
            fail("PATH is expected to be set in the test environment");
        }
    }
}
