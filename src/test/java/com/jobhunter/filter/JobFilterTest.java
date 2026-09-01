package com.jobhunter.filter;

import com.jobhunter.model.FilterCriteria;
import com.jobhunter.model.Job;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

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
