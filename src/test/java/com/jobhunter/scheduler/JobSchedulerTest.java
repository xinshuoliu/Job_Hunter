package com.jobhunter.scheduler;

import com.jobhunter.api.JobProvider;
import com.jobhunter.filter.JobFilter;
import com.jobhunter.filter.JobScorer;
import com.jobhunter.model.FilterCriteria;
import com.jobhunter.model.Job;
import com.jobhunter.notification.Notifier;
import com.jobhunter.storage.JobRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JobSchedulerTest {

    private static final String TEST_DB = "test-scheduler.db";

    private JobRepository repository;
    private RecordingNotifier notifier;

    @BeforeEach
    void setUp() {
        new File(TEST_DB).delete();
        repository = new JobRepository(TEST_DB);
        notifier = new RecordingNotifier();
    }

    @AfterEach
    void tearDown() {
        new File(TEST_DB).delete();
    }

    @Test
    @DisplayName("The same posting from two providers in separate cycles is notified once")
    void deduplicatesAcrossCyclesDespiteDifferentLocationStrings() {
        Job fromJSearch = job("jsearch-1", "jsearch", "Software Developer Intern", "Coveo", "Montreal, CA");
        Job fromAdzuna = job("adzuna-9", "adzuna", "Software Developer Intern", "Coveo", "Montréal, Québec");

        scheduler(List.of(provider("jsearch", fromJSearch))).runCycle();
        scheduler(List.of(provider("adzuna", fromAdzuna))).runCycle();

        assertEquals(1, notifier.deliveredJobs.size(), "job should only be announced once");
        assertEquals(1, repository.count(), "only one row should be stored");
    }

    @Test
    @DisplayName("Two postings that differ only by location are stored once")
    void collapsesLocationVariantsWithinOneCycle() {
        Job dorval = job("adzuna-1", "adzuna", "Test Engineering Intern", "Grass Valley", "Dorval, Montréal");
        Job montreal = job("adzuna-2", "adzuna", "Test Engineering Intern", "Grass Valley", "Montréal, Québec");

        scheduler(List.of(provider("adzuna", dorval, montreal))).runCycle();

        assertEquals(1, repository.count());
        assertEquals(1, notifier.deliveredJobs.size());
    }

    @Test
    @DisplayName("Jobs are not marked notified when delivery fails")
    void failedDeliveryLeavesJobsUnnotified() {
        notifier.succeed = false;

        scheduler(List.of(provider("adzuna", job("adzuna-1", "adzuna", "Java Intern", "Acme", "Montreal")))).runCycle();

        assertEquals(1, repository.getUnnotifiedJobs().size(), "failed delivery must stay pending");
    }

    @Test
    @DisplayName("A job that failed to deliver is retried on the next cycle")
    void pendingJobsAreRetriedNextCycle() {
        Job posting = job("adzuna-1", "adzuna", "Java Intern", "Acme", "Montreal");

        notifier.succeed = false;
        scheduler(List.of(provider("adzuna", posting))).runCycle();
        assertEquals(1, repository.getUnnotifiedJobs().size());

        notifier.succeed = true;
        scheduler(List.of()).runCycle();

        assertEquals(0, repository.getUnnotifiedJobs().size(), "retry should clear the backlog");
        assertEquals(2, notifier.notifyCalls, "the job should have been attempted twice");
        assertEquals("Java Intern", notifier.deliveredJobs.get(0).getTitle());
    }

    @Test
    @DisplayName("A provider is skipped until its interval has elapsed")
    void providerIntervalIsRespected() {
        CountingProvider provider = new CountingProvider("adzuna", 6,
                job("adzuna-1", "adzuna", "Java Intern", "Acme", "Montreal"));

        scheduler(List.of(provider)).runCycle();
        scheduler(List.of(provider)).runCycle();

        assertEquals(1, provider.fetchCalls, "second cycle should skip the provider");
    }

    @Test
    @DisplayName("A cycle with no results sends a status message instead of a notification")
    void emptyCycleSendsStatusOnly() {
        scheduler(List.of(provider("adzuna"))).runCycle();

        assertEquals(0, notifier.notifyCalls);
        assertEquals(1, notifier.statusMessages.size());
    }

    @Test
    @DisplayName("Status messages are throttled to the configured interval")
    void statusMessagesAreThrottled() {
        scheduler(List.of(provider("adzuna"))).runCycle();
        scheduler(List.of(provider("adzuna"))).runCycle();

        assertEquals(1, notifier.statusMessages.size());
    }

    @Test
    @DisplayName("A status interval of zero disables status messages")
    void zeroStatusIntervalDisablesStatus() {
        scheduler(List.of(provider("adzuna")), SETTINGS.withStatusIntervalHours(0)).runCycle();

        assertTrue(notifier.statusMessages.isEmpty());
    }

    @Test
    @DisplayName("Reworded reposts of the same job are only announced once")
    void fuzzyDuplicatesAcrossCyclesAreSkipped() {
        scheduler(List.of(provider("jsearch",
                job("jsearch-1", "jsearch", "Software Engineer Intern, Backend (Summer 2027)", "Lyft", "Montreal, CA")))).runCycle();
        scheduler(List.of(provider("simplify",
                job("simplify-1", "simplify", "Software Engineer Intern - Backend Development at Lyft", "Lyft, Inc.", "Montreal, QC")))).runCycle();

        assertEquals(1, notifier.deliveredJobs.size());
        assertEquals(1, repository.count());
    }

    @Test
    @DisplayName("Reworded duplicates in one cycle are merged and keep both links")
    void fuzzyDuplicatesWithinCycleAreMerged() {
        Job first = job("jsearch-1", "jsearch", "Core Algorithms Software Developer Intern", "Kinaxis", "Montreal");
        Job second = job("adzuna-1", "adzuna", "Co-op/Intern Software Developer, Core Algorithms", "Kinaxis Inc.", "Montreal");

        scheduler(List.of(provider("mixed", first, second))).runCycle();

        assertEquals(1, notifier.deliveredJobs.size());
        Job delivered = notifier.deliveredJobs.get(0);
        assertEquals("jsearch · adzuna", delivered.getSource());
        assertEquals(List.of("https://example.com/adzuna-1"), delivered.getAdditionalUrls());
    }

    @Test
    @DisplayName("The same role for a different term is treated as a new posting")
    void differentTermsAreNotDuplicates() {
        scheduler(List.of(provider("adzuna",
                job("adzuna-1", "adzuna", "Intern, Software (S-02-08) Fall 2026", "Matrox", "Montreal"),
                job("adzuna-2", "adzuna", "Intern, Software (S-02-08) Winter 2027", "Matrox", "Montreal")))).runCycle();

        assertEquals(2, notifier.deliveredJobs.size());
    }

    @Test
    @DisplayName("Scored jobs below min_score are dropped and the rest are sent best first")
    void scoringFiltersAndOrders() {
        JobScorer scorer = new JobScorer(Map.of("java", 3, "backend", 2, "technical writer", -10), 1);
        Job writer = job("a-1", "adzuna", "Technical Writer Intern", "Acme", "Montreal");
        Job java = job("a-2", "adzuna", "Java Backend Intern", "Globex", "Montreal");
        Job backend = job("a-3", "adzuna", "Backend Intern", "Initech", "Montreal");

        new JobScheduler(List.of(provider("adzuna", writer, backend, java)), new JobFilter(new FilterCriteria()),
                scorer, repository, List.of(notifier), SETTINGS, CLOCK).runCycle();

        assertEquals(List.of("Java Backend Intern", "Backend Intern"),
                notifier.deliveredJobs.stream().map(Job::getTitle).toList());
        assertEquals(5, notifier.deliveredJobs.get(0).getScore());
    }

    @Test
    @DisplayName("Digest mode queues jobs until the digest hour, then sends them once")
    void digestModeWaitsForDigestHour() {
        JobScheduler.Settings digest = SETTINGS.withDigest(18);
        Job posting = job("adzuna-1", "adzuna", "Java Intern", "Acme", "Montreal");

        scheduler(List.of(provider("adzuna", posting)), digest, at("2026-10-13T09:00:00")).runCycle();
        assertEquals(0, notifier.notifyCalls, "nothing should be sent before the digest hour");
        assertEquals(1, repository.getUnnotifiedJobs().size());

        scheduler(List.of(), digest, at("2026-10-13T18:30:00")).runCycle();
        assertEquals(1, notifier.notifyCalls);
        assertEquals(0, repository.getUnnotifiedJobs().size());

        scheduler(List.of(provider("adzuna2", job("adzuna-2", "adzuna", "Python Intern", "Globex", "Montreal"))),
                digest, at("2026-10-13T22:00:00")).runCycle();
        assertEquals(1, notifier.notifyCalls, "only one digest per day");

        scheduler(List.of(), digest, at("2026-10-14T18:05:00")).runCycle();
        assertEquals(2, notifier.notifyCalls);
    }

    private static final JobScheduler.Settings SETTINGS =
            JobScheduler.Settings.defaults("software developer", "Montreal");
    private static final Clock CLOCK = at("2026-10-13T12:00:00");

    private static Clock at(String localDateTime) {
        ZoneId zone = ZoneId.of("America/Montreal");
        return Clock.fixed(LocalDateTime.parse(localDateTime).atZone(zone).toInstant(), zone);
    }

    private JobScheduler scheduler(List<JobProvider> providers) {
        return scheduler(providers, SETTINGS);
    }

    private JobScheduler scheduler(List<JobProvider> providers, JobScheduler.Settings settings) {
        return scheduler(providers, settings, CLOCK);
    }

    private JobScheduler scheduler(List<JobProvider> providers, JobScheduler.Settings settings, Clock clock) {
        return new JobScheduler(providers, new JobFilter(new FilterCriteria(), clock), JobScorer.disabled(),
                repository, List.of(notifier), settings, clock);
    }

    private static JobProvider provider(String name, Job... jobs) {
        return new CountingProvider(name, 0, jobs);
    }

    private static Job job(String id, String source, String title, String company, String location) {
        Job job = new Job();
        job.setId(id);
        job.setSource(source);
        job.setTitle(title);
        job.setCompany(company);
        job.setLocation(location);
        job.setUrl("https://example.com/" + id);
        return job;
    }

    private static class CountingProvider implements JobProvider {

        private final String name;
        private final int intervalHours;
        private final List<Job> jobs;
        private int fetchCalls;

        CountingProvider(String name, int intervalHours, Job... jobs) {
            this.name = name;
            this.intervalHours = intervalHours;
            this.jobs = List.of(jobs);
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public int getMaxPages() {
            return 1;
        }

        @Override
        public int getIntervalHours() {
            return intervalHours;
        }

        @Override
        public List<Job> fetchJobs(String query, String location, int page) {
            fetchCalls++;
            return jobs;
        }
    }

    private static class RecordingNotifier implements Notifier {

        private final List<Job> deliveredJobs = new ArrayList<>();
        private final List<String> statusMessages = new ArrayList<>();
        private boolean succeed = true;
        private int notifyCalls;

        @Override
        public boolean notify(List<Job> jobs) {
            notifyCalls++;
            if (succeed) {
                deliveredJobs.addAll(jobs);
            }
            return succeed;
        }

        @Override
        public void notifyStatus(String message) {
            statusMessages.add(message);
        }
    }
}
