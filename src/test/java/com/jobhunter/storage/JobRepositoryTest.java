package com.jobhunter.storage;

import com.jobhunter.model.Job;
import org.junit.jupiter.api.*;

import java.io.File;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobRepositoryTest {

    private static final String TEST_DB = "test-jobs.db";
    private JobRepository repo;

    @BeforeEach
    void setUp() {
        new File(TEST_DB).delete();
        repo = new JobRepository(TEST_DB);
    }

    @AfterEach
    void tearDown() {
        new File(TEST_DB).delete();
    }

    @Test
    @DisplayName("Saving a new job returns true")
    void saveNewJob() {
        Job job = createJob("1", "Java Dev", "Acme", "Montreal");
        assertTrue(repo.save(job));
        assertEquals(1, repo.count());
    }

    @Test
    @DisplayName("Saving a duplicate job returns false")
    void saveDuplicateJob() {
        Job job1 = createJob("adzuna-1", "Java Dev", "Acme", "Montreal");
        Job job2 = createJob("arbeitnow-99", "Java Dev", "Acme", "Montreal");

        assertTrue(repo.save(job1));
        assertFalse(repo.save(job2));
        assertEquals(1, repo.count());
    }

    @Test
    @DisplayName("Different jobs are saved separately")
    void saveDifferentJobs() {
        Job job1 = createJob("1", "Java Dev", "Acme", "Montreal");
        Job job2 = createJob("2", "Python Dev", "Globex", "Toronto");

        assertTrue(repo.save(job1));
        assertTrue(repo.save(job2));
        assertEquals(2, repo.count());
    }

    @Test
    @DisplayName("saveAll returns only new jobs")
    void saveAllReturnsNewOnly() {
        Job existing = createJob("1", "Java Dev", "Acme", "Montreal");
        repo.save(existing);

        Job duplicate = createJob("2", "Java Dev", "Acme", "Montreal");
        Job newJob = createJob("3", "Go Dev", "NewCorp", "Remote");

        List<Job> result = repo.saveAll(List.of(duplicate, newJob));
        assertEquals(1, result.size());
        assertEquals("Go Dev", result.get(0).getTitle());
    }

    @Test
    @DisplayName("Unnotified jobs are returned correctly")
    void getUnnotifiedJobs() {
        Job job = createJob("1", "Java Dev", "Acme", "Montreal");
        repo.save(job);

        List<Job> unnotified = repo.getUnnotifiedJobs();
        assertEquals(1, unnotified.size());

        repo.markAsNotified(unnotified);

        unnotified = repo.getUnnotifiedJobs();
        assertEquals(0, unnotified.size());
    }

    @Test
    @DisplayName("Postings that differ only by location are stored once")
    void locationVariantsAreDeduplicated() {
        Job jsearch = createJob("jsearch-1", "Software Developer Intern", "Coveo", "Montreal, CA");
        Job adzuna = createJob("adzuna-1", "Software Developer Intern", "Coveo", "Montréal, Québec");

        assertTrue(repo.save(jsearch));
        assertFalse(repo.save(adzuna));
        assertEquals(1, repo.count());
    }

    @Test
    @DisplayName("Pruning removes notified jobs older than the retention window")
    void pruneRemovesOldNotifiedJobs() {
        Job old = createJob("1", "Java Dev", "Acme", "Montreal");
        old.setFetchedAt(Instant.now().minus(400, ChronoUnit.DAYS));
        repo.save(old);
        repo.markAsNotified(List.of(old));

        assertEquals(1, repo.pruneOlderThan(365));
        assertEquals(0, repo.count());
    }

    @Test
    @DisplayName("Pruning keeps jobs that were never notified")
    void pruneKeepsUnnotifiedJobs() {
        Job old = createJob("1", "Java Dev", "Acme", "Montreal");
        old.setFetchedAt(Instant.now().minus(400, ChronoUnit.DAYS));
        repo.save(old);

        assertEquals(0, repo.pruneOlderThan(365));
        assertEquals(1, repo.count());
    }

    @Test
    @DisplayName("Pruning is disabled when retention is zero")
    void pruneDisabledWhenRetentionIsZero() {
        Job old = createJob("1", "Java Dev", "Acme", "Montreal");
        old.setFetchedAt(Instant.now().minus(400, ChronoUnit.DAYS));
        repo.save(old);
        repo.markAsNotified(List.of(old));

        assertEquals(0, repo.pruneOlderThan(0));
        assertEquals(1, repo.count());
    }

    private Job createJob(String id, String title, String company, String location) {
        Job job = new Job();
        job.setId("test-" + id);
        job.setSource("test");
        job.setTitle(title);
        job.setCompany(company);
        job.setLocation(location);
        job.setPostedAt(Instant.now());
        return job;
    }
}
