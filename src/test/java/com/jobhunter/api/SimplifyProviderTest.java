package com.jobhunter.api;

import com.jobhunter.http.Http;
import com.jobhunter.model.Job;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SimplifyProviderTest {

    private static final Instant NOW = Instant.parse("2026-10-13T12:00:00Z");

    @Test
    @DisplayName("Only active, visible and recent listings are kept")
    void keepsActiveRecentListings() throws IOException {
        List<Job> jobs = parse(45);

        assertEquals(1, jobs.size());
        Job job = jobs.get(0);
        assertEquals("simplify-active-recent", job.getId());
        assertEquals("simplify", job.getSource());
        assertEquals("Software Developer Intern", job.getTitle());
        assertEquals("Squarepoint Capital", job.getCompany());
        assertEquals("Montreal, QC, Canada; NYC", job.getLocation());
        assertEquals("Software | Summer 2027, Fall 2027", job.getDescription());
        assertEquals(Instant.ofEpochSecond(1791849600L), job.getPostedAt());
    }

    @Test
    @DisplayName("A max age of zero keeps old active listings")
    void zeroMaxAgeKeepsEverythingActive() throws IOException {
        List<Job> jobs = parse(0);

        assertEquals(List.of("simplify-active-recent", "simplify-stale"),
                jobs.stream().map(Job::getId).toList());
    }

    private List<Job> parse(int maxAgeDays) throws IOException {
        SimplifyProvider provider = new SimplifyProvider(Http.client(), List.of(), maxAgeDays);
        return provider.parse(new StringReader(Fixtures.read("simplify.json")), NOW);
    }
}
