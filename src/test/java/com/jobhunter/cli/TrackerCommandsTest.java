package com.jobhunter.cli;

import com.jobhunter.model.ApplicationStatus;
import com.jobhunter.model.Job;
import com.jobhunter.storage.JobRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class TrackerCommandsTest {

    private static final String TEST_DB = "test-tracker.db";

    private JobRepository repository;
    private ByteArrayOutputStream buffer;
    private TrackerCommands tracker;

    @BeforeEach
    void setUp() {
        new File(TEST_DB).delete();
        repository = new JobRepository(TEST_DB);
        buffer = new ByteArrayOutputStream();
        tracker = new TrackerCommands(repository, new PrintStream(buffer, true, StandardCharsets.UTF_8));

        Job job = new Job();
        job.setId("lever-mirego-1");
        job.setSource("lever");
        job.setTitle("Stagiaire en développement logiciel");
        job.setCompany("Mirego");
        job.setUrl("https://jobs.lever.co/mirego/1");
        job.setPostedAt(Instant.parse("2026-10-10T00:00:00Z"));
        job.setScore(4);
        repository.save(job);
    }

    @AfterEach
    void tearDown() {
        new File(TEST_DB).delete();
    }

    @Test
    @DisplayName("list prints row id, status, score, title and link")
    void listsJobs() {
        assertEquals(0, tracker.list(CommandLine.parse(new String[]{"list"})));

        String out = output();
        assertTrue(out.contains("#1"), out);
        assertTrue(out.contains("new"), out);
        assertTrue(out.contains("[4]"), out);
        assertTrue(out.contains("Stagiaire en développement logiciel — Mirego"), out);
        assertTrue(out.contains("https://jobs.lever.co/mirego/1"), out);
    }

    @Test
    @DisplayName("mark updates the status by row id or job id")
    void marksJobs() {
        assertEquals(0, tracker.mark(CommandLine.parse(new String[]{"mark", "1", "applied"})));
        assertEquals(ApplicationStatus.APPLIED, repository.find("1").orElseThrow().getStatus());

        assertEquals(0, tracker.mark(CommandLine.parse(new String[]{"mark", "lever-mirego-1", "interview"})));
        assertEquals(ApplicationStatus.INTERVIEW, repository.find("1").orElseThrow().getStatus());
        assertTrue(output().contains("is now 'interview'"));
    }

    @Test
    @DisplayName("mark rejects unknown jobs and statuses")
    void markValidatesInput() {
        assertEquals(1, tracker.mark(CommandLine.parse(new String[]{"mark", "42", "applied"})));
        assertEquals(2, tracker.mark(CommandLine.parse(new String[]{"mark", "1", "hired"})));
        assertEquals(2, tracker.mark(CommandLine.parse(new String[]{"mark", "1"})));
        assertEquals(ApplicationStatus.NEW, repository.find("1").orElseThrow().getStatus());
    }

    @Test
    @DisplayName("list can filter by status")
    void listFiltersByStatus() {
        assertEquals(0, tracker.list(CommandLine.parse(new String[]{"list", "--status", "applied"})));
        assertTrue(output().contains("No jobs with status 'applied'"));
        assertEquals(2, tracker.list(CommandLine.parse(new String[]{"list", "--status", "bogus"})));
    }

    @Test
    @DisplayName("stats summarises by status and source")
    void printsStats() {
        tracker.mark(CommandLine.parse(new String[]{"mark", "1", "applied"}));
        buffer.reset();

        assertEquals(0, tracker.stats());
        String out = output();
        assertTrue(out.matches("(?s).*applied\\s+1.*"), out);
        assertTrue(out.matches("(?s).*total\\s+1.*"), out);
        assertTrue(out.matches("(?s).*lever\\s+1.*"), out);
    }

    private String output() {
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
