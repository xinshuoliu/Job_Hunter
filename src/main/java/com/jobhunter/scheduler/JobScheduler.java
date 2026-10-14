package com.jobhunter.scheduler;

import com.jobhunter.api.JobProvider;
import com.jobhunter.dedup.DuplicateDetector;
import com.jobhunter.filter.JobFilter;
import com.jobhunter.filter.JobScorer;
import com.jobhunter.model.Job;
import com.jobhunter.notification.Notifier;
import com.jobhunter.storage.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class JobScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);

    private static final int PAGE_SIZE_HINT = 20;
    private static final String STATUS_RUN_KEY = "status-update";
    private static final String DIGEST_RUN_KEY = "digest";

    public record Settings(String searchQuery, String searchLocation, int retentionDays,
                           int statusIntervalHours, boolean digest, int digestHour, int dedupWindowDays) {

        public static Settings defaults(String searchQuery, String searchLocation) {
            return new Settings(searchQuery, searchLocation, 0, 24, false, 18, 120);
        }

        public Settings withRetentionDays(int days) {
            return new Settings(searchQuery, searchLocation, days, statusIntervalHours, digest, digestHour, dedupWindowDays);
        }

        public Settings withStatusIntervalHours(int hours) {
            return new Settings(searchQuery, searchLocation, retentionDays, hours, digest, digestHour, dedupWindowDays);
        }

        public Settings withDigest(int hour) {
            return new Settings(searchQuery, searchLocation, retentionDays, statusIntervalHours, true, hour, dedupWindowDays);
        }

        public Settings withDedupWindowDays(int days) {
            return new Settings(searchQuery, searchLocation, retentionDays, statusIntervalHours, digest, digestHour, days);
        }
    }

    private final List<JobProvider> providers;
    private final JobFilter filter;
    private final JobScorer scorer;
    private final DuplicateDetector detector;
    private final JobRepository repository;
    private final List<Notifier> notifiers;
    private final Settings settings;
    private final Clock clock;
    private final ScheduledExecutorService executor;

    public JobScheduler(List<JobProvider> providers, JobFilter filter, JobScorer scorer,
                        JobRepository repository, List<Notifier> notifiers, Settings settings) {
        this(providers, filter, scorer, repository, notifiers, settings, Clock.systemDefaultZone());
    }

    public JobScheduler(List<JobProvider> providers, JobFilter filter, JobScorer scorer,
                        JobRepository repository, List<Notifier> notifiers, Settings settings, Clock clock) {
        this.providers = providers;
        this.filter = filter;
        this.scorer = scorer;
        this.detector = new DuplicateDetector();
        this.repository = repository;
        this.notifiers = notifiers;
        this.settings = settings;
        this.clock = clock;
        this.executor = Executors.newSingleThreadScheduledExecutor();
    }

    public void start(int intervalMinutes) {
        log.info("Starting Job Hunter — polling every {} minutes", intervalMinutes);
        log.info("Search: '{}' in '{}'", settings.searchQuery(), settings.searchLocation());
        log.info("Providers: {}", providers.stream().map(JobProvider::getName).toList());
        if (settings.digest()) {
            log.info("Digest mode: notifications are sent once a day after {}:00", settings.digestHour());
        }

        executor.scheduleAtFixedRate(this::runCycle, 0, intervalMinutes, TimeUnit.MINUTES);
    }

    public void runCycle() {
        log.info("--- Starting fetch cycle ---");

        try {
            List<Job> fetched = fetchFromProviders();

            List<Job> newJobs = List.of();
            if (!fetched.isEmpty()) {
                List<Job> matching = scorer.apply(filter.filter(fetched));
                List<Job> candidates = collapseDuplicates(matching);
                if (candidates.size() < matching.size()) {
                    log.info("Collapsed {} matches into {} unique listings", matching.size(), candidates.size());
                }
                newJobs = repository.saveAll(withoutKnownDuplicates(candidates));
            }

            List<Job> pending = JobScorer.byScoreDescending(withPendingRetries(newJobs));

            if (settings.digest()) {
                runDigest(pending, newJobs.size());
            } else if (pending.isEmpty()) {
                log.info("No new jobs to notify");
                sendStatusIfDue();
            } else {
                log.info("🔔 {} job(s) to notify ({} new, {} retried)",
                        pending.size(), newJobs.size(), pending.size() - newJobs.size());
                deliver(pending);
            }

            repository.pruneOlderThan(settings.retentionDays());

            log.info("--- Cycle complete. Total jobs in DB: {} ---", repository.count());

        } catch (Exception e) {
            log.error("Fetch cycle failed: {}", e.getMessage(), e);
        }
    }

    private void runDigest(List<Job> pending, int newCount) {
        if (!isDigestDue()) {
            log.info("Digest mode: {} new, {} queued for the next digest", newCount, pending.size());
            return;
        }
        if (pending.isEmpty()) {
            log.info("Digest due but nothing new to report");
            sendStatusIfDue();
            repository.markRan(DIGEST_RUN_KEY, clock.instant());
            return;
        }
        log.info("📬 Sending daily digest with {} job(s)", pending.size());
        if (deliver(pending)) {
            repository.markRan(DIGEST_RUN_KEY, clock.instant());
        }
    }

    boolean isDigestDue() {
        ZonedDateTime now = ZonedDateTime.now(clock);
        Instant todaysDigest = LocalDate.now(clock).atTime(settings.digestHour(), 0).atZone(clock.getZone()).toInstant();
        if (now.toInstant().isBefore(todaysDigest)) return false;

        Optional<Instant> last = repository.lastRun(DIGEST_RUN_KEY);
        return last.isEmpty() || last.get().isBefore(todaysDigest);
    }

    private record FetchResult(List<Job> jobs, Exception failure) {
    }

    private List<Job> fetchFromProviders() {
        List<JobProvider> due = new ArrayList<>();
        for (JobProvider provider : providers) {
            if (repository.shouldProviderRun(provider.getName(), provider.getIntervalHours())) {
                due.add(provider);
            } else {
                log.info("Skipping {} — not due yet (interval: {}h)", provider.getName(), provider.getIntervalHours());
            }
        }

        List<Job> allJobs = new ArrayList<>();
        try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<FetchResult>> results = new ArrayList<>();
            for (JobProvider provider : due) {
                results.add(pool.submit(() -> fetchAllPages(provider)));
            }

            for (int i = 0; i < due.size(); i++) {
                JobProvider provider = due.get(i);
                FetchResult result = await(results.get(i));
                allJobs.addAll(result.jobs());
                if (result.failure() == null) {
                    repository.markProviderRan(provider.getName());
                } else {
                    log.error("Failed to fetch from {}: {}", provider.getName(), result.failure().getMessage());
                }
            }
        }

        if (allJobs.isEmpty()) {
            log.info("No jobs fetched from any provider");
        }
        return allJobs;
    }

    private FetchResult fetchAllPages(JobProvider provider) {
        List<Job> jobs = new ArrayList<>();
        try {
            for (int page = 1; page <= provider.getMaxPages(); page++) {
                List<Job> pageJobs = provider.fetchJobs(settings.searchQuery(), settings.searchLocation(), page);
                if (pageJobs.isEmpty()) break;
                jobs.addAll(pageJobs);
                log.info("Fetched {} jobs from {} (page {})", pageJobs.size(), provider.getName(), page);
                if (pageJobs.size() < PAGE_SIZE_HINT) break;
            }
            return new FetchResult(jobs, null);
        } catch (Exception e) {
            return new FetchResult(jobs, e);
        }
    }

    private static FetchResult await(Future<FetchResult> future) {
        try {
            return future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new FetchResult(List.of(), e);
        } catch (ExecutionException e) {
            return new FetchResult(List.of(), e);
        }
    }

    private void sendStatusIfDue() {
        int hours = settings.statusIntervalHours();
        if (hours <= 0 || !repository.shouldProviderRun(STATUS_RUN_KEY, hours)) {
            return;
        }
        for (Notifier notifier : notifiers) {
            try {
                notifier.notifyStatus("✅ Job Hunter is running — no new matching jobs since the last check.");
            } catch (Exception e) {
                log.error("Status update failed: {}", e.getMessage());
            }
        }
        repository.markProviderRan(STATUS_RUN_KEY);
    }

    private List<Job> withoutKnownDuplicates(List<Job> candidates) {
        if (settings.dedupWindowDays() <= 0) return candidates;

        DuplicateDetector.Index recent = detector.index(
                repository.findFetchedSince(clock.instant().minus(settings.dedupWindowDays(), ChronoUnit.DAYS)));
        List<Job> fresh = new ArrayList<>();
        for (Job job : candidates) {
            Optional<Job> known = recent.find(job);
            if (known.isPresent() && !known.get().getId().equals(job.getId())) {
                log.info("Skipping likely duplicate of #{} '{}': {}", known.get().getRowId(), known.get().getTitle(), job);
            } else {
                fresh.add(job);
            }
        }
        return fresh;
    }

    private List<Job> withPendingRetries(List<Job> newJobs) {
        List<Job> pending = new ArrayList<>(newJobs);

        Set<String> alreadyIncluded = new HashSet<>();
        for (Job job : newJobs) {
            alreadyIncluded.add(job.getId());
        }

        for (Job job : repository.getUnnotifiedJobs()) {
            if (alreadyIncluded.add(job.getId())) {
                pending.add(job);
            }
        }

        return pending;
    }

    private boolean deliver(List<Job> jobs) {
        boolean allDelivered = true;

        for (Notifier notifier : notifiers) {
            try {
                if (!notifier.notify(jobs)) {
                    allDelivered = false;
                    log.error("Notifier {} reported a delivery failure", notifier.getClass().getSimpleName());
                }
            } catch (Exception e) {
                allDelivered = false;
                log.error("Notification failed: {}", e.getMessage());
            }
        }

        if (allDelivered) {
            repository.markAsNotified(jobs);
        } else {
            log.warn("Leaving {} job(s) unnotified — they will be retried next cycle", jobs.size());
        }
        return allDelivered;
    }

    private List<Job> collapseDuplicates(List<Job> jobs) {
        List<Job> unique = new ArrayList<>();
        DuplicateDetector.Index index = detector.index(List.of());
        for (Job job : jobs) {
            Optional<Job> existing = index.find(job);
            if (existing.isPresent()) {
                mergeInto(existing.get(), job);
            } else {
                unique.add(job);
                index.add(job);
            }
        }
        return unique;
    }

    private static void mergeInto(Job kept, Job duplicate) {
        List<String> sources = Arrays.asList(kept.getSource().split(" · "));
        if (!sources.contains(duplicate.getSource())) {
            kept.setSource(kept.getSource() + " · " + duplicate.getSource());
        }
        if (duplicate.getUrl() != null && !duplicate.getUrl().equals(kept.getUrl())
                && (kept.getAdditionalUrls() == null || !kept.getAdditionalUrls().contains(duplicate.getUrl()))) {
            kept.addAdditionalUrl(duplicate.getUrl());
        }
        if (duplicate.getScore() != null && (kept.getScore() == null || duplicate.getScore() > kept.getScore())) {
            kept.setScore(duplicate.getScore());
        }
    }

    public void stop() {
        log.info("Shutting down Job Hunter...");
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
