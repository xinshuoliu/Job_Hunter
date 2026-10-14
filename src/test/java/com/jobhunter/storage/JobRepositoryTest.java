package com.jobhunter.storage;

import com.jobhunter.model.ApplicationStatus;
import com.jobhunter.model.Job;
import org.junit.jupiter.api.*;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
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

    @Test
    @DisplayName("Company suffixes do not defeat exact deduplication")
    void companySuffixesAreIgnored() {
        assertTrue(repo.save(createJob("1", "Intern Cloud Developer", "Kinaxis", "Montreal")));
        assertFalse(repo.save(createJob("2", "Intern Cloud Developer", "Kinaxis Inc.", "Montreal")));
    }

    @Test
    @DisplayName("Jobs can be found by row number or id and given a status")
    void tracksApplicationStatus() {
        repo.save(createJob("1", "Java Dev", "Acme", "Montreal"));
        Job stored = repo.list(null, 10).get(0);

        assertEquals(ApplicationStatus.NEW, stored.getStatus());
        assertTrue(repo.updateStatus(String.valueOf(stored.getRowId()), ApplicationStatus.APPLIED));

        Job updated = repo.find("test-1").orElseThrow();
        assertEquals(ApplicationStatus.APPLIED, updated.getStatus());
        assertNotNull(updated.getStatusUpdatedAt());
        assertEquals(1, repo.list(ApplicationStatus.APPLIED, 10).size());
        assertEquals(0, repo.list(ApplicationStatus.NEW, 10).size());
        assertEquals(1, repo.countByStatus().get(ApplicationStatus.APPLIED));
        assertFalse(repo.updateStatus("999", ApplicationStatus.APPLIED));
    }

    @Test
    @DisplayName("Pruning keeps jobs you are actively tracking")
    void pruneKeepsTrackedJobs() {
        Job old = createJob("1", "Java Dev", "Acme", "Montreal");
        old.setFetchedAt(Instant.now().minus(400, ChronoUnit.DAYS));
        repo.save(old);
        repo.markAsNotified(List.of(old));
        repo.updateStatus("test-1", ApplicationStatus.INTERVIEW);

        assertEquals(0, repo.pruneOlderThan(365));
        assertEquals(1, repo.count());
    }

    @Test
    @DisplayName("Scores are stored and read back")
    void storesScore() {
        Job job = createJob("1", "Java Dev", "Acme", "Montreal");
        job.setScore(7);
        repo.save(job);

        assertEquals(7, repo.getUnnotifiedJobs().get(0).getScore());
    }

    @Test
    @DisplayName("A schema v1 database is backed up, upgraded and re-deduplicated")
    void migratesFromSchemaV1() throws Exception {
        String legacy = "test-legacy.db";
        new File(legacy).delete();
        new File(legacy + ".v1.bak").delete();
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + legacy);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("""
                    CREATE TABLE jobs (id TEXT PRIMARY KEY, source TEXT NOT NULL, title TEXT, company TEXT,
                        location TEXT, description TEXT, url TEXT, salary_min REAL, salary_max REAL, currency TEXT,
                        posted_at TEXT, fetched_at TEXT, dedup_key TEXT, notified INTEGER DEFAULT 0)
                    """);
            stmt.executeUpdate("CREATE UNIQUE INDEX idx_dedup_key ON jobs(dedup_key)");
            stmt.executeUpdate("""
                    INSERT INTO jobs (id, source, title, company, fetched_at, dedup_key, notified) VALUES
                    ('a', 'jsearch', 'Intern Cloud Developer', 'Kinaxis', '2026-07-12T00:00:00Z', 'intern cloud developer|kinaxis', 1),
                    ('b', 'adzuna', 'Intern Cloud Developer', 'Kinaxis Inc.', '2026-07-13T00:00:00Z', 'intern cloud developer|kinaxis inc', 0)
                    """);
            stmt.executeUpdate("PRAGMA user_version = 1");
        }

        try {
            JobRepository migrated = new JobRepository(legacy);

            assertTrue(new File(legacy + ".v1.bak").exists(), "a backup should be written before migrating");
            assertEquals(1, migrated.count());
            assertEquals(0, migrated.getUnnotifiedJobs().size(), "the surviving row inherits the notified flag");
            assertEquals(ApplicationStatus.NEW, migrated.find("a").orElseThrow().getStatus());
        } finally {
            new File(legacy).delete();
            new File(legacy + ".v1.bak").delete();
        }
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
