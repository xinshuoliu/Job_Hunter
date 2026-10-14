package com.jobhunter.scheduler;

import com.jobhunter.api.JobProvider;
import com.jobhunter.filter.JobFilter;
import com.jobhunter.model.FilterCriteria;
import com.jobhunter.model.Job;
import com.jobhunter.notification.Notifier;
import com.jobhunter.storage.JobRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

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
        new JobScheduler(List.of(provider("adzuna")), new JobFilter(new FilterCriteria()), repository,
                List.of(notifier), "software developer", "Montreal", 0, 0).runCycle();

        assertTrue(notifier.statusMessages.isEmpty());
    }

    private JobScheduler scheduler(List<JobProvider> providers) {
        return new JobScheduler(providers, new JobFilter(new FilterCriteria()), repository,
                List.of(notifier), "software developer", "Montreal", 0);
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
