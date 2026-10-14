package com.jobhunter.scheduler;

import com.jobhunter.api.JobProvider;
import com.jobhunter.filter.JobFilter;
import com.jobhunter.model.Job;
import com.jobhunter.notification.Notifier;
import com.jobhunter.storage.JobRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class JobScheduler {

    private static final Logger log = LoggerFactory.getLogger(JobScheduler.class);

    private static final int PAGE_SIZE_HINT = 20;
    private static final String STATUS_RUN_KEY = "status-update";
    private static final int DEFAULT_STATUS_INTERVAL_HOURS = 24;

    private final List<JobProvider> providers;
    private final JobFilter filter;
    private final JobRepository repository;
    private final List<Notifier> notifiers;
    private final String searchQuery;
    private final String searchLocation;
    private final int retentionDays;
    private final int statusIntervalHours;
    private final ScheduledExecutorService executor;

    public JobScheduler(List<JobProvider> providers, JobFilter filter,
                        JobRepository repository, List<Notifier> notifiers,
                        String searchQuery, String searchLocation) {
        this(providers, filter, repository, notifiers, searchQuery, searchLocation, 0, DEFAULT_STATUS_INTERVAL_HOURS);
    }

    public JobScheduler(List<JobProvider> providers, JobFilter filter,
                        JobRepository repository, List<Notifier> notifiers,
                        String searchQuery, String searchLocation, int retentionDays) {
        this(providers, filter, repository, notifiers, searchQuery, searchLocation,
                retentionDays, DEFAULT_STATUS_INTERVAL_HOURS);
    }

    public JobScheduler(List<JobProvider> providers, JobFilter filter,
                        JobRepository repository, List<Notifier> notifiers,
                        String searchQuery, String searchLocation, int retentionDays,
                        int statusIntervalHours) {
        this.providers = providers;
        this.filter = filter;
        this.repository = repository;
        this.notifiers = notifiers;
        this.searchQuery = searchQuery;
        this.searchLocation = searchLocation;
        this.retentionDays = retentionDays;
        this.statusIntervalHours = statusIntervalHours;
        this.executor = Executors.newSingleThreadScheduledExecutor();
    }

    public void start(int intervalMinutes) {
        log.info("Starting Job Hunter — polling every {} minutes", intervalMinutes);
        log.info("Search: '{}' in '{}'", searchQuery, searchLocation);
        log.info("Providers: {}", providers.stream().map(JobProvider::getName).toList());

        executor.scheduleAtFixedRate(this::runCycle, 0, intervalMinutes, TimeUnit.MINUTES);
    }

    public void runCycle() {
        log.info("--- Starting fetch cycle ---");

        try {
            List<Job> fetched = fetchFromProviders();

            List<Job> newJobs = List.of();
            if (!fetched.isEmpty()) {
                List<Job> matching = filter.filter(fetched);
                List<Job> candidates = collapseDuplicates(matching);
                if (candidates.size() < matching.size()) {
                    log.info("Collapsed {} matches into {} unique listings", matching.size(), candidates.size());
                }
                newJobs = repository.saveAll(candidates);
            }

            List<Job> pending = withPendingRetries(newJobs);

            if (pending.isEmpty()) {
                log.info("No new jobs to notify");
                sendStatusIfDue();
            } else {
                log.info("🔔 {} job(s) to notify ({} new, {} retried)",
                        pending.size(), newJobs.size(), pending.size() - newJobs.size());
                deliver(pending);
            }

            repository.pruneOlderThan(retentionDays);

            log.info("--- Cycle complete. Total jobs in DB: {} ---", repository.count());

        } catch (Exception e) {
            log.error("Fetch cycle failed: {}", e.getMessage(), e);
        }
    }

    private List<Job> fetchFromProviders() {
        List<Job> allJobs = new ArrayList<>();

        for (JobProvider provider : providers) {
            if (!repository.shouldProviderRun(provider.getName(), provider.getIntervalHours())) {
                log.info("Skipping {} — not due yet (interval: {}h)", provider.getName(), provider.getIntervalHours());
                continue;
            }
            try {
                for (int page = 1; page <= provider.getMaxPages(); page++) {
                    List<Job> jobs = provider.fetchJobs(searchQuery, searchLocation, page);
                    if (jobs.isEmpty()) break;
                    allJobs.addAll(jobs);
                    log.info("Fetched {} jobs from {} (page {})", jobs.size(), provider.getName(), page);
                    if (jobs.size() < PAGE_SIZE_HINT) break;
                }
                repository.markProviderRan(provider.getName());
            } catch (Exception e) {
                log.error("Failed to fetch from {}: {}", provider.getName(), e.getMessage());
            }
        }

        if (allJobs.isEmpty()) {
            log.info("No jobs fetched from any provider");
        }
        return allJobs;
    }

    private void sendStatusIfDue() {
        if (statusIntervalHours <= 0 || !repository.shouldProviderRun(STATUS_RUN_KEY, statusIntervalHours)) {
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

    private void deliver(List<Job> jobs) {
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
    }

    private List<Job> collapseDuplicates(List<Job> jobs) {
        Map<String, Job> seen = new LinkedHashMap<>();
        for (Job job : jobs) {
            Job existing = seen.get(job.deduplicationKey());
            if (existing != null) {
                existing.setSource(existing.getSource() + " · " + job.getSource());
                existing.addAdditionalUrl(job.getUrl());
            } else {
                seen.put(job.deduplicationKey(), job);
            }
        }
        return new ArrayList<>(seen.values());
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
