package com.jobhunter.api;

import com.jobhunter.config.AppConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProviderRegistryTest {

    private Path config;

    @AfterEach
    void tearDown() throws IOException {
        if (config != null) Files.deleteIfExists(config);
    }

    @Test
    @DisplayName("Providers are built only when enabled and configured")
    void buildsConfiguredProviders() throws IOException {
        AppConfig cfg = load("""
                apis:
                  adzuna: { app_id: "id", app_key: "key" }
                providers:
                  remotive: true
                  simplify: true
                  jsearch: true
                boards:
                  lever: [mirego]
                """);

        List<String> names = ProviderRegistry.build(cfg).stream().map(JobProvider::getName).toList();

        assertEquals(List.of("adzuna", "remotive", "lever", "simplify"), names);
    }

    @Test
    @DisplayName("Board providers are wrapped with the board location scope")
    void scopesBoards() throws IOException {
        AppConfig cfg = load("""
                providers: { adzuna: false, jsearch: false, jsearch_linkedin: false }
                boards:
                  locations: ["Montreal"]
                  ashby: [cohere]
                """);

        List<JobProvider> providers = ProviderRegistry.build(cfg);

        assertEquals(1, providers.size());
        assertInstanceOf(LocationScopedProvider.class, providers.get(0));
        assertEquals("ashby", providers.get(0).getName());
    }

    @Test
    @DisplayName("Every registered toggle name is unique")
    void togglesAreUnique() {
        List<String> toggles = ProviderRegistry.entries().stream().map(ProviderRegistry.Entry::toggle).toList();
        assertEquals(toggles.size(), toggles.stream().distinct().count());
    }

    private AppConfig load(String yaml) throws IOException {
        config = Files.createTempFile("job-hunter", ".yml");
        Files.writeString(config, yaml);
        return AppConfig.load(config.toString());
    }
}
