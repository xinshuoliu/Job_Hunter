package com.jobhunter;

import com.jobhunter.api.JobProvider;
import com.jobhunter.api.ProviderRegistry;
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
        List<JobProvider> providers = ProviderRegistry.build(config);
        log.info("Enabled providers: {}", providers.stream().map(JobProvider::getName).toList());
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
