package com.jobhunter.filter;

import com.jobhunter.model.FilterCriteria;
import com.jobhunter.model.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class JobFilterTest {

    private FilterCriteria criteria;

    @BeforeEach
    void setUp() {
        criteria = new FilterCriteria();
    }

    @Test
    @DisplayName("Job matching a keyword should pass the filter")
    void matchesKeyword() {
        criteria.setKeywords(List.of("java", "spring"));
        JobFilter filter = new JobFilter(criteria);

        Job job = createJob("Java Developer", "Acme Corp", "Montreal");
        assertTrue(filter.matches(job));
    }

    @Test
    @DisplayName("Job not matching any keyword should be rejected")
    void rejectsNonMatchingKeyword() {
        criteria.setKeywords(List.of("python", "django"));
        JobFilter filter = new JobFilter(criteria);

        Job job = createJob("Java Developer", "Acme Corp", "Montreal");
        assertFalse(filter.matches(job));
    }

    @Test
    @DisplayName("Job matching an exclude keyword should be rejected")
    void rejectsExcludedKeyword() {
        criteria.setKeywords(List.of("java"));
        criteria.setExcludeKeywords(List.of("senior", "principal"));
        JobFilter filter = new JobFilter(criteria);

        Job job = createJob("Senior Java Developer", "Acme Corp", "Montreal");
        assertFalse(filter.matches(job));
    }

    @Test
    @DisplayName("Job in a desired location should pass")
    void matchesLocation() {
        criteria.setLocations(List.of("Montreal", "Remote"));
        JobFilter filter = new JobFilter(criteria);

        Job job = createJob("Developer", "Acme", "Montreal, QC");
        assertTrue(filter.matches(job));
    }

    @Test
    @DisplayName("Job in wrong location should be rejected")
    void rejectsWrongLocation() {
        criteria.setLocations(List.of("Montreal"));
        JobFilter filter = new JobFilter(criteria);

        Job job = createJob("Developer", "Acme", "Vancouver, BC");
        assertFalse(filter.matches(job));
    }

    @Test
    @DisplayName("Job below minimum salary should be rejected")
    void rejectsBelowMinSalary() {
        criteria.setMinSalary(80000.0);
        JobFilter filter = new JobFilter(criteria);

        Job job = createJob("Developer", "Acme", "Montreal");
        job.setSalaryMax(60000.0);
        assertFalse(filter.matches(job));
    }

    @Test
    @DisplayName("Job without salary should pass when min salary is set")
    void passesWhenNoSalaryListed() {
        criteria.setMinSalary(80000.0);
        JobFilter filter = new JobFilter(criteria);

        Job job = createJob("Developer", "Acme", "Montreal");
        assertTrue(filter.matches(job));
    }

    @Test
    @DisplayName("Empty criteria should let everything through")
    void emptyFilterPassesEverything() {
        JobFilter filter = new JobFilter(criteria);

        Job job = createJob("Anything", "Anywhere", "Everywhere");
        assertTrue(filter.matches(job));
    }

    @Test
    @DisplayName("filter() should return only matching jobs from a list")
    void filterList() {
        criteria.setKeywords(List.of("java"));
        JobFilter filter = new JobFilter(criteria);

        List<Job> jobs = List.of(
                createJob("Java Developer", "A", "Montreal"),
                createJob("Python Developer", "B", "Montreal"),
                createJob("Java Spring Engineer", "C", "Toronto")
        );

        List<Job> result = filter.filter(jobs);
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("Keywords match whole words only")
    void keywordsMatchWholeWords() {
        criteria.setRequiredKeywords(List.of("intern", "stage"));
        JobFilter filter = new JobFilter(criteria);

        assertTrue(filter.matches(createJob("Software Developer Intern", "Acme", "Montreal")));
        assertTrue(filter.matches(createJob("Stage en développement", "Acme", "Montreal")));
        assertFalse(filter.matches(createJob("Internal Tools Developer", "Acme", "Montreal")));
        assertFalse(filter.matches(createJob("International Sales Rep", "Acme", "Montreal")));
        assertFalse(filter.matches(createJob("Backstage Technician", "Acme", "Montreal")));
    }

    @Test
    @DisplayName("Multi-word and hyphenated keywords match")
    void matchesPhrases() {
        criteria.setRequiredKeywords(List.of("co-op", "new grad"));
        JobFilter filter = new JobFilter(criteria);

        assertTrue(filter.matches(createJob("Software Co-op (Winter 2027)", "Acme", "Montreal")));
        assertTrue(filter.matches(createJob("New Grad Software Engineer", "Acme", "Montreal")));
        assertFalse(filter.matches(createJob("Cooperative Manager", "Acme", "Montreal")));
    }

    @Test
    @DisplayName("Location matching ignores accents")
    void locationIgnoresAccents() {
        criteria.setLocations(List.of("Montreal"));
        JobFilter filter = new JobFilter(criteria);

        assertTrue(filter.matches(createJob("Developer", "Acme", "Montréal, Québec")));
    }

    @Test
    @DisplayName("Exclude keywords only check the title by default")
    void excludeChecksTitleByDefault() {
        criteria.setExcludeKeywords(List.of("senior"));
        JobFilter filter = new JobFilter(criteria);

        Job job = createJob("Software Intern", "Acme", "Montreal");
        job.setDescription("You will pair with senior engineers.");
        assertTrue(filter.matches(job));
    }

    @Test
    @DisplayName("Exclude scope ALL also checks the description")
    void excludeScopeAllChecksDescription() {
        criteria.setExcludeKeywords(List.of("senior"));
        criteria.setExcludeScope(FilterCriteria.ExcludeScope.ALL);
        JobFilter filter = new JobFilter(criteria);

        Job job = createJob("Software Intern", "Acme", "Montreal");
        job.setDescription("You will pair with senior engineers.");
        assertFalse(filter.matches(job));
    }

    @Test
    @DisplayName("Postings older than max_age_days are rejected, undated ones pass")
    void rejectsStalePostings() {
        criteria.setMaxAgeDays(30);
        JobFilter filter = new JobFilter(criteria, NOW);

        Job fresh = createJob("Java Intern", "Acme", "Montreal");
        fresh.setPostedAt(Instant.parse("2026-10-01T00:00:00Z"));
        Job stale = createJob("Java Intern", "Acme", "Montreal");
        stale.setPostedAt(Instant.parse("2026-08-01T00:00:00Z"));
        Job undated = createJob("Java Intern", "Acme", "Montreal");

        assertTrue(filter.matches(fresh));
        assertFalse(filter.matches(stale));
        assertTrue(filter.matches(undated));
    }

    @Test
    @DisplayName("Titles that only name past years are rejected")
    void rejectsPastYearTitles() {
        JobFilter filter = new JobFilter(criteria, NOW);

        assertFalse(filter.matches(createJob("Mobile Engineering Intern - Summer 2025", "Mercury", "Montreal")));
        assertTrue(filter.matches(createJob("Intern, Software Fall 2026", "Matrox", "Montreal")));
        assertTrue(filter.matches(createJob("Web Developer Internship (2025-2027)", "Workland", "Montreal")));
        assertTrue(filter.matches(createJob("Software Intern", "Acme", "Montreal")));
    }

    @Test
    @DisplayName("Past-year rejection can be turned off")
    void pastYearRejectionCanBeDisabled() {
        criteria.setRejectPastYears(false);
        JobFilter filter = new JobFilter(criteria, NOW);

        assertTrue(filter.matches(createJob("Mobile Engineering Intern - Summer 2025", "Mercury", "Montreal")));
    }

    @Test
    @DisplayName("A title naming an excluded city is rejected unless it also names a wanted one")
    void rejectsExcludedLocationsInTitle() {
        criteria.setLocations(List.of("Montreal"));
        criteria.setExcludeLocations(List.of("Ottawa", "Toronto"));
        JobFilter filter = new JobFilter(criteria, NOW);

        assertFalse(filter.matches(createJob("Lumentum Embedded Software Intern Ottawa", "Lumentum", "Montreal, CA")));
        assertTrue(filter.matches(createJob("Software Intern - Montreal or Toronto", "Acme", "Montreal, CA")));
        assertTrue(filter.matches(createJob("Software Intern", "Acme", "Montreal, CA; Toronto")));
    }

    @Test
    @DisplayName("Blocklisted companies are rejected")
    void rejectsExcludedCompanies() {
        criteria.setExcludeCompanies(List.of("hanzilla", "Wayne State University"));
        JobFilter filter = new JobFilter(criteria, NOW);

        assertFalse(filter.matches(createJob("Front-End Developer Intern at Coveo", "jobs.hanzilla.co - Jobboard", "Montreal")));
        assertFalse(filter.matches(createJob("Summer Internship at Uber", "Wayne State University", "Montreal")));
        assertTrue(filter.matches(createJob("Frontend Developer Intern", "Coveo", "Montreal")));
    }

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-10-13T12:00:00Z"), ZoneOffset.UTC);

    private Job createJob(String title, String company, String location) {
        Job job = new Job();
        job.setId("test-" + System.nanoTime());
        job.setSource("test");
        job.setTitle(title);
        job.setCompany(company);
        job.setLocation(location);
        return job;
    }
}
