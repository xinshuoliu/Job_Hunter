package com.jobhunter.api;

import com.jobhunter.config.AppConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ProviderRegistry {

    public record Entry(String toggle, boolean enabledByDefault, boolean board,
                        Predicate<AppConfig> configured, Function<AppConfig, JobProvider> factory) {
    }

    private static final List<Entry> ENTRIES = List.of(
            search("adzuna", true, c -> present(c.getAdzunaAppId(), c.getAdzunaAppKey()),
                    c -> new AdzunaProvider(c.getAdzunaAppId(), c.getAdzunaAppKey(), c.getAdzunaCountry())),
            search("jsearch", true, c -> present(c.getJSearchApiKey()),
                    c -> new JSearchProvider(c.getJSearchApiKey())),
            search("jsearch_linkedin", true, c -> present(c.getJSearchApiKey()),
                    c -> new JSearchProvider(c.getJSearchApiKey(), "linkedin") {
                        @Override public String getName() { return "jsearch-linkedin"; }
                        @Override public int getIntervalHours() { return 12; }
                    }),
            search("arbeitnow", false, c -> true, c -> new ArbeitnowProvider()),
            search("remotive", false, c -> true, c -> new RemotiveProvider()),
            board("greenhouse", true, c -> !c.getGreenhouseBoards().isEmpty(),
                    c -> new GreenhouseProvider(c.getGreenhouseBoards())),
            board("lever", true, c -> !c.getLeverBoards().isEmpty(),
                    c -> new LeverProvider(c.getLeverBoards())),
            board("ashby", true, c -> !c.getAshbyBoards().isEmpty(),
                    c -> new AshbyProvider(c.getAshbyBoards())),
            board("simplify", false, c -> true,
                    c -> new SimplifyProvider(c.getSimplifyUrls(), c.getSimplifyMaxAgeDays())));

    private ProviderRegistry() {
    }

    public static List<Entry> entries() {
        return ENTRIES;
    }

    public static List<JobProvider> build(AppConfig config) {
        return build(config, ENTRIES);
    }

    static List<JobProvider> build(AppConfig config, List<Entry> entries) {
        List<JobProvider> providers = new ArrayList<>();
        for (Entry entry : entries) {
            if (!config.isProviderEnabled(entry.toggle(), entry.enabledByDefault()) || !entry.configured().test(config)) {
                continue;
            }
            JobProvider provider = entry.factory().apply(config);
            providers.add(entry.board() ? LocationScopedProvider.scope(provider, config.getBoardLocations()) : provider);
        }
        return providers;
    }

    private static Entry search(String toggle, boolean enabledByDefault, Predicate<AppConfig> configured,
                                Function<AppConfig, JobProvider> factory) {
        return new Entry(toggle, enabledByDefault, false, configured, factory);
    }

    private static Entry board(String toggle, boolean enabledByDefault, Predicate<AppConfig> configured,
                               Function<AppConfig, JobProvider> factory) {
        return new Entry(toggle, enabledByDefault, true, configured, factory);
    }

    private static boolean present(String... values) {
        for (String value : values) {
            if (value == null || value.isBlank()) return false;
        }
        return true;
    }
}
