package com.jobhunter;

import com.jobhunter.api.AdzunaProvider;
import com.jobhunter.api.ArbeitnowProvider;
import com.jobhunter.api.JSearchProvider;
import com.jobhunter.api.RemotiveProvider;
import com.jobhunter.api.JobProvider;
import com.jobhunter.config.AppConfig;
import com.jobhunter.filter.JobFilter;
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
        String configPath = args.length > 0 ? args[0] : "config.yml";

        log.info("=== Job Hunter Starting ===");
        log.info("Loading config from: {}", configPath);

        AppConfig config = AppConfig.load(configPath);

        List<JobProvider> providers = buildProviders(config);

        if (providers.isEmpty()) {
            log.error("No API providers configured! Check the 'providers' and 'apis' sections of {}", configPath);
            System.exit(1);
        }

        JobFilter filter = new JobFilter(config.getFilterCriteria());

        JobRepository repository = new JobRepository(config.getDbPath());

        List<Notifier> notifiers = buildNotifiers(config);

        JobScheduler scheduler = new JobScheduler(
                providers, filter, repository, notifiers,
                config.getSearchQuery(), config.getSearchLocation(),
                config.getRetentionDays(), config.getStatusIntervalHours()
        );

        Runtime.getRuntime().addShutdownHook(new Thread(scheduler::stop, "job-hunter-shutdown"));

        int intervalMinutes = config.getPollIntervalMinutes();
        if (intervalMinutes <= 0) {
            log.warn("poll_interval_minutes must be positive, falling back to 60");
            intervalMinutes = 60;
        }

        scheduler.start(intervalMinutes);
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
