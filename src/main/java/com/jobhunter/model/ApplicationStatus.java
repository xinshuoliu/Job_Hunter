package com.jobhunter.model;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public enum ApplicationStatus {
    NEW,
    INTERESTED,
    APPLIED,
    INTERVIEW,
    OFFER,
    REJECTED,
    IGNORED;

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public boolean isTracked() {
        return this == INTERESTED || this == APPLIED || this == INTERVIEW || this == OFFER;
    }

    public static Optional<ApplicationStatus> parse(String value) {
        if (value == null) return Optional.empty();
        String wanted = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(values()).filter(s -> s.name().equals(wanted)).findFirst();
    }

    public static String allKeys() {
        return Arrays.stream(values()).map(ApplicationStatus::key).collect(Collectors.joining(", "));
    }
}
