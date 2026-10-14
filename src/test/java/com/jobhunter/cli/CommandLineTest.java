package com.jobhunter.cli;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandLineTest {

    @Test
    @DisplayName("No arguments runs the poller with config.yml")
    void defaults() {
        CommandLine cmd = CommandLine.parse(new String[0]);
        assertEquals("config.yml", cmd.configPath());
        assertEquals("run", cmd.command());
    }

    @Test
    @DisplayName("A bare YAML path is still accepted as the config, as the Docker image passes it")
    void positionalConfigStillWorks() {
        CommandLine cmd = CommandLine.parse(new String[]{"/app/config.yml"});
        assertEquals("/app/config.yml", cmd.configPath());
        assertEquals("run", cmd.command());
    }

    @Test
    @DisplayName("--once and the once command are equivalent")
    void onceFlag() {
        assertEquals("once", CommandLine.parse(new String[]{"--once"}).command());
        assertEquals("once", CommandLine.parse(new String[]{"prod.yaml", "once"}).command());
    }

    @Test
    @DisplayName("Options and positional arguments are separated")
    void parsesArguments() {
        CommandLine cmd = CommandLine.parse(new String[]{"--config", "x.yml", "list", "--status", "applied", "--limit", "5"});
        assertEquals("x.yml", cmd.configPath());
        assertEquals("list", cmd.command());
        assertEquals("applied", cmd.option("--status"));
        assertEquals("5", cmd.option("--limit"));
        assertNull(cmd.option("--missing"));

        CommandLine mark = CommandLine.parse(new String[]{"mark", "12", "applied"});
        assertEquals(List.of("12", "applied"), mark.positional());
    }

    @Test
    @DisplayName("--config without a value is an error")
    void missingConfigValue() {
        assertThrows(IllegalArgumentException.class, () -> CommandLine.parse(new String[]{"--config"}));
    }
}
