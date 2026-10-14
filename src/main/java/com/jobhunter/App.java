package com.jobhunter;

import com.jobhunter.api.AdzunaProvider;
import com.jobhunter.api.ArbeitnowProvider;
import com.jobhunter.api.AshbyProvider;
import com.jobhunter.api.GreenhouseProvider;
import com.jobhunter.api.JSearchProvider;
import com.jobhunter.api.LeverProvider;
import com.jobhunter.api.RemotiveProvider;
import com.jobhunter.api.JobProvider;
import com.jobhunter.api.SimplifyProvider;
import com.jobhunter.cli.CommandLine;
import com.jobhunter.cli.TrackerCommands;
import com.jobhunter.config.AppConfig;
import com.jobhunter.filter.JobFilter;
import com.jobhunter.filter.JobScorer;
import com.jobhunter.model.ApplicationStatus;
import com.jobhunter.notification.ConsoleNotifier;
import com.jobhunter.notification.DiscordNotifier;
import com.jobhunter.notification.EmailNotifier;
import com.jobhunter.notification.Notifier;
import com.jobhunter.scheduler.JobScheduler;
import com.jobhunter.storage.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.jobhunter.api.LocationScopedProvider.scope;

public class App {

    private static final Logger log = LoggerFactory.getLogger(App.class);

    public static void main(String[] args) {
        CommandLine cmd;
        try {
            cmd = CommandLine.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.exit(2);
            return;
        }

        int exitCode = switch (cmd.command()) {
            case "run", "once" -> runScheduler(cmd);
            case "list", "mark", "stats" -> runTracker(cmd);
            case "help" -> printUsage(0);
            default -> {
                System.err.println("Unknown command '" + cmd.command() + "'");
                yield printUsage(2);
            }
        };

        if (!cmd.command().equals("run") || exitCode != 0) {
            System.exit(exitCode);
        }
    }

    private static int printUsage(int exitCode) {
        System.out.printf(CommandLine.USAGE, ApplicationStatus.allKeys());
        return exitCode;
    }

    private static int runTracker(CommandLine cmd) {
        try {
            AppConfig config = AppConfig.load(cmd.configPath());
            TrackerCommands tracker = new TrackerCommands(new JobRepository(config.getDbPath()), System.out);
            return switch (cmd.command()) {
                case "list" -> tracker.list(cmd);
                case "mark" -> tracker.mark(cmd);
                default -> tracker.stats();
            };
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            return 2;
        }
    }

    private static int runScheduler(CommandLine cmd) {
        log.info("=== Job Hunter Starting ===");
        log.info("Loading config from: {}", cmd.configPath());

        AppConfig config = AppConfig.load(cmd.configPath());

        List<JobProvider> providers = buildProviders(config);

        if (providers.isEmpty()) {
            log.error("No API providers configured! Check the 'providers' and 'apis' sections of {}", cmd.configPath());
            return 1;
        }

        JobFilter filter = new JobFilter(config.getFilterCriteria());
        JobScorer scorer = new JobScorer(config.getScoringWeights(), config.getMinScore());
        JobRepository repository = new JobRepository(config.getDbPath());
        List<Notifier> notifiers = buildNotifiers(config);

        JobScheduler.Settings settings = JobScheduler.Settings
                .defaults(config.getSearchQuery(), config.getSearchLocation())
                .withRetentionDays(config.getRetentionDays())
                .withStatusIntervalHours(config.getStatusIntervalHours())
                .withDedupWindowDays(config.getDedupWindowDays());
        if (config.isDigestMode()) {
            settings = settings.withDigest(config.getDigestHour());
        }

        JobScheduler scheduler = new JobScheduler(providers, filter, scorer, repository, notifiers, settings);

        if (cmd.command().equals("once")) {
            scheduler.runCycle();
            scheduler.stop();
            return 0;
        }

        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::stop, "job-hunter-shutdown"));

        int intervalMinutes = config.getPollIntervalMinutes();
        if (intervalMinutes <= 0) {
            log.warn("poll_interval_minutes must be positive, falling back to 60");
            intervalMinutes = 60;
        }

        scheduler.start(intervalMinutes);
        return 0;
    }

    private static List<JobProvider> buildProviders(AppConfig config) {
        List<JobProvider> providers = new ArrayList<>();

        boolean hasAdzunaKeys = !config.getAdzunaAppId().isEmpty() && !config.getAdzunaAppKey().isEmpty();
        if (config.isProviderEnabled("adzuna", true) && hasAdzunaKeys) {
            providers.add(new AdzunaProvider(
                    config.getAdzunaAppId(),
                    config.getAdzunaAppKey(),
                    config.getAdzunaCountry()
            ));
        }

        boolean hasJSearchKey = !config.getJSearchApiKey().isEmpty();
        if (config.isProviderEnabled("jsearch", true) && hasJSearchKey) {
            providers.add(new JSearchProvider(config.getJSearchApiKey()));
        }
        if (config.isProviderEnabled("jsearch_linkedin", true) && hasJSearchKey) {
            providers.add(new JSearchProvider(config.getJSearchApiKey(), "linkedin") {
                @Override public String getName() { return "jsearch-linkedin"; }
                @Override public int getIntervalHours() { return 12; }
            });
        }

        if (config.isProviderEnabled("arbeitnow", false)) {
            providers.add(new ArbeitnowProvider());
        }

        if (config.isProviderEnabled("remotive", false)) {
            providers.add(new RemotiveProvider());
        }

        List<String> boardLocations = config.getBoardLocations();

        if (config.isProviderEnabled("greenhouse", true) && !config.getGreenhouseBoards().isEmpty()) {
            providers.add(scope(new GreenhouseProvider(config.getGreenhouseBoards()), boardLocations));
        }

        if (config.isProviderEnabled("lever", true) && !config.getLeverBoards().isEmpty()) {
            providers.add(scope(new LeverProvider(config.getLeverBoards()), boardLocations));
        }

        if (config.isProviderEnabled("ashby", true) && !config.getAshbyBoards().isEmpty()) {
            providers.add(scope(new AshbyProvider(config.getAshbyBoards()), boardLocations));
        }

        if (config.isProviderEnabled("simplify", false)) {
            providers.add(scope(new SimplifyProvider(config.getSimplifyUrls(), config.getSimplifyMaxAgeDays()), boardLocations));
        }

        log.info("Enabled providers: {}",providers.stream().map(JobProvider::getName).toList());
        return providers;
    }

    private static List<Notifier> buildNotifiers(AppConfig config) {
        List<Notifier> notifiers = new ArrayList<>();

        if (config.isConsoleEnabled()) {
            notifiers.add(new ConsoleNotifier());
        }
        if (config.isDiscordEnabled() && !config.getDiscordWebhookUrl().isEmpty()) {
            notifiers.add(new DiscordNotifier(config.getDiscordWebhookUrl(), config.getDiscordMentionUserId()));
        }
        if (config.isEmailEnabled()) {
            notifiers.add(new EmailNotifier(
                    config.getSmtpHost(), config.getSmtpPort(),
                    config.getSmtpUsername(), config.getSmtpPassword(),
                    config.getEmailFrom(), config.getEmailTo()
            ));
        }

        return notifiers;
    }
}
