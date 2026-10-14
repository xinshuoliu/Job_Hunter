package com.jobhunter.config;

import com.jobhunter.model.FilterCriteria;
import com.jobhunter.model.FilterCriteria.ExcludeScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AppConfig {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    static final String DEFAULT_SIMPLIFY_URL =
            "https://raw.githubusercontent.com/SimplifyJobs/Summer2027-Internships/dev/.github/scripts/listings.json";

    private static final Pattern PLACEHOLDER =Pattern.compile("\\$\\{([A-Za-z_][A-Za-z0-9_]*)(?::-([^}]*))?\\}");

    private String adzunaAppId;
    private String adzunaAppKey;
    private String adzunaCountry;

    private String jSearchApiKey;

    private String searchQuery;
    private String searchLocation;
    private int pollIntervalMinutes;

    private FilterCriteria filterCriteria;

    private Map<String, Boolean> providerToggles = Map.of();

    private Map<String, String> greenhouseBoards = Map.of();
    private Map<String, String> leverBoards = Map.of();
    private Map<String, String> ashbyBoards = Map.of();
    private List<String> boardLocations = List.of();
    private List<String> simplifyUrls = List.of();
    private int simplifyMaxAgeDays;

    private int statusIntervalHours;
    private boolean consoleEnabled;
    private boolean discordEnabled;
    private String discordWebhookUrl;
    private String discordMentionUserId;
    private boolean emailEnabled;
    private String smtpHost;
    private int smtpPort;
    private String smtpUsername;
    private String smtpPassword;
    private String emailFrom;
    private String emailTo;

    private String dbPath;
    private int retentionDays;

    @SuppressWarnings("unchecked")
    public static AppConfig load(String filePath) {
        AppConfig config = new AppConfig();
        Yaml yaml = new Yaml();

        try (InputStream input = new FileInputStream(filePath)) {
            Map<String, Object> root = yaml.load(input);

            Map<String, Object> apis = (Map<String, Object>) root.getOrDefault("apis", Map.of());
            Map<String, Object> adzuna = (Map<String, Object>) apis.getOrDefault("adzuna", Map.of());
            config.adzunaAppId = getString(adzuna, "app_id", "");
            config.adzunaAppKey = getString(adzuna, "app_key", "");
            config.adzunaCountry = getString(adzuna, "country", "ca");

            Map<String, Object> jsearch = (Map<String, Object>) apis.getOrDefault("jsearch", Map.of());
            config.jSearchApiKey = getString(jsearch, "api_key", "");

            Map<String, Object> search = (Map<String, Object>) root.getOrDefault("search", Map.of());
            config.searchQuery = getString(search, "query", "java developer");
            config.searchLocation = getString(search, "location", "Montreal");
            config.pollIntervalMinutes = getInt(search, "poll_interval_minutes", 30);

            Map<String, Object> filters = (Map<String, Object>) root.getOrDefault("filters", Map.of());
            FilterCriteria criteria = new FilterCriteria();
            criteria.setRequiredKeywords((List<String>) filters.getOrDefault("required_keywords", List.of()));
            criteria.setKeywords((List<String>) filters.getOrDefault("keywords", List.of()));
            criteria.setExcludeKeywords((List<String>) filters.getOrDefault("exclude_keywords", List.of()));
            criteria.setLocations((List<String>) filters.getOrDefault("locations", List.of()));
            if (filters.containsKey("min_salary")) {
                criteria.setMinSalary(((Number) filters.get("min_salary")).doubleValue());
            }
            criteria.setCurrency(getString(filters, "currency", "CAD"));
            criteria.setExcludeScope(parseExcludeScope(getString(filters, "exclude_scope", "title")));
            config.filterCriteria = criteria;

            Map<String, Object> providers = (Map<String, Object>) root.getOrDefault("providers", Map.of());
            Map<String, Boolean> toggles = new HashMap<>();
            providers.forEach((name, value) -> {
                if (value instanceof Boolean enabled) {
                    toggles.put(name, enabled);
                }
            });
            config.providerToggles = Map.copyOf(toggles);

            Map<String, Object> boards = (Map<String, Object>) root.getOrDefault("boards", Map.of());
            config.greenhouseBoards = parseBoards(boards.get("greenhouse"));
            config.leverBoards = parseBoards(boards.get("lever"));
            config.ashbyBoards = parseBoards(boards.get("ashby"));
            config.boardLocations = ((List<Object>) boards.getOrDefault("locations", List.of())).stream()
                    .map(Object::toString)
                    .toList();

            Map<String, Object> simplify = (Map<String, Object>) boards.getOrDefault("simplify", Map.of());
            List<String> simplifyUrls = ((List<Object>) simplify.getOrDefault("urls", List.of())).stream()
                    .map(url -> resolve(url.toString()))
                    .toList();
            config.simplifyUrls = simplifyUrls.isEmpty() ? List.of(DEFAULT_SIMPLIFY_URL) : simplifyUrls;
            config.simplifyMaxAgeDays = getInt(simplify, "max_age_days", 45);

            Map<String, Object> notifications =(Map<String, Object>) root.getOrDefault("notifications", Map.of());

            config.statusIntervalHours = getInt(notifications, "status_interval_hours", 24);

            Map<String, Object> console =(Map<String, Object>) notifications.getOrDefault("console", Map.of());
            config.consoleEnabled = getBoolean(console, "enabled", true);

            Map<String, Object> discord = (Map<String, Object>) notifications.getOrDefault("discord", Map.of());
            config.discordEnabled = getBoolean(discord, "enabled", false);
            config.discordWebhookUrl = getString(discord, "webhook_url", "");
            config.discordMentionUserId = getString(discord, "mention_user_id", "");

            Map<String, Object> email = (Map<String, Object>) notifications.getOrDefault("email", Map.of());
            config.emailEnabled = getBoolean(email, "enabled", false);
            config.smtpHost = getString(email, "smtp_host", "smtp.gmail.com");
            config.smtpPort = getInt(email, "smtp_port", 587);
            config.smtpUsername = getString(email, "username", "");
            config.smtpPassword = getString(email, "password", "");
            config.emailFrom = getString(email, "from", "");
            config.emailTo = getString(email, "to", "");

            Map<String, Object> database = (Map<String, Object>) root.getOrDefault("database", Map.of());
            config.dbPath = getString(database, "path", "jobs.db");
            config.retentionDays = getInt(database, "retention_days", 365);

            log.info("Configuration loaded from {}", filePath);

        } catch (IOException e) {
            log.error("Failed to load config from {}: {}", filePath, e.getMessage());
            throw new RuntimeException("Config load failed", e);
        }

        return config;
    }

    static String resolve(String value) {
        if (value == null || value.indexOf("${") < 0) return value;

        Matcher matcher = PLACEHOLDER.matcher(value);
        StringBuilder resolved = new StringBuilder();

        while (matcher.find()) {
            String name = matcher.group(1);
            String fallback = matcher.group(2);
            String fromEnv = System.getenv(name);

            String replacement;
            if (fromEnv != null && !fromEnv.isEmpty()) {
                replacement = fromEnv;
            } else if (fallback != null) {
                replacement = fallback;
            } else {
                log.warn("Environment variable {} is not set — using an empty value", name);
                replacement = "";
            }

            matcher.appendReplacement(resolved, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(resolved);

        return resolved.toString();
    }

    static Map<String, String> parseBoards(Object value) {
        Map<String, String> boards = new LinkedHashMap<>();
        if (value instanceof Map<?, ?> map) {
            map.forEach((slug, name) -> boards.put(slug.toString().trim(), name == null ? "" : name.toString().trim()));
        } else if (value instanceof List<?> list) {
            for (Object entry : list) {
                if (entry instanceof Map<?, ?> named) {
                    boards.putAll(parseBoards(named));
                } else if (entry != null) {
                    boards.put(entry.toString().trim(), "");
                }
            }
        }
        boards.remove("");
        return Collections.unmodifiableMap(boards);
    }

    static ExcludeScope parseExcludeScope(String value) {
        try {
            return ExcludeScope.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            log.warn("Unknown exclude_scope '{}' — expected 'title' or 'all', using 'title'", value);
            return ExcludeScope.TITLE;
        }
    }

    private static String getString(Map<String, Object> map, String key, String defaultVal) {
        Object val = map.get(key);
        return val != null ? resolve(val.toString()) : defaultVal;
    }

    private static int getInt(Map<String, Object> map, String key, int defaultVal) {
        Object val = map.get(key);
        return val instanceof Number ? ((Number) val).intValue() : defaultVal;
    }

    private static boolean getBoolean(Map<String, Object> map, String key, boolean defaultVal) {
        Object val = map.get(key);
        return val instanceof Boolean ? (Boolean) val : defaultVal;
    }

    public boolean isProviderEnabled(String name, boolean defaultVal) {
        return providerToggles.getOrDefault(name, defaultVal);
    }

    public String getAdzunaAppId() { return adzunaAppId; }
    public String getAdzunaAppKey() { return adzunaAppKey; }
    public String getAdzunaCountry() { return adzunaCountry; }
    public String getJSearchApiKey() { return jSearchApiKey; }
    public String getSearchQuery() { return searchQuery; }
    public String getSearchLocation() { return searchLocation; }
    public int getPollIntervalMinutes() { return pollIntervalMinutes; }
    public FilterCriteria getFilterCriteria() { return filterCriteria; }
    public Map<String, String> getGreenhouseBoards() { return greenhouseBoards; }
    public Map<String, String> getLeverBoards() { return leverBoards; }
    public Map<String, String> getAshbyBoards() { return ashbyBoards; }
    public List<String> getBoardLocations() { return boardLocations; }
    public List<String> getSimplifyUrls(){ return simplifyUrls; }
    public int getSimplifyMaxAgeDays() { return simplifyMaxAgeDays; }
    public int getStatusIntervalHours(){ return statusIntervalHours; }
    public boolean isConsoleEnabled(){ return consoleEnabled; }
    public boolean isDiscordEnabled() { return discordEnabled; }
    public String getDiscordWebhookUrl() { return discordWebhookUrl; }
    public String getDiscordMentionUserId() { return discordMentionUserId; }
    public boolean isEmailEnabled() { return emailEnabled; }
    public String getSmtpHost() { return smtpHost; }
    public int getSmtpPort() { return smtpPort; }
    public String getSmtpUsername() { return smtpUsername; }
    public String getSmtpPassword() { return smtpPassword; }
    public String getEmailFrom() { return emailFrom; }
    public String getEmailTo() { return emailTo; }
    public String getDbPath() { return dbPath; }
    public int getRetentionDays() { return retentionDays; }
}
