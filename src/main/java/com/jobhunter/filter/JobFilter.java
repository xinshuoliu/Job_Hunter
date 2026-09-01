package com.jobhunter.filter;

import com.jobhunter.model.FilterCriteria;
import com.jobhunter.model.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class JobFilter {

    private static final Logger log = LoggerFactory.getLogger(JobFilter.class);

    private final FilterCriteria criteria;

    public JobFilter(FilterCriteria criteria) {
        this.criteria = criteria;
    }

    public boolean matches(Job job) {
        return matchesRequiredKeywords(job)
                && matchesKeywords(job)
                && !matchesExcludeKeywords(job)
                && matchesLocation(job)
                && matchesMinSalary(job);
    }

    public List<Job> filter(List<Job> jobs) {
        List<Job> filtered = jobs.stream().filter(this::matches).toList();
        log.info("Filtered {} jobs down to {} matches", jobs.size(), filtered.size());
        return filtered;
    }

    private boolean matchesRequiredKeywords(Job job) {
        if (criteria.getRequiredKeywords() == null || criteria.getRequiredKeywords().isEmpty()) {
            return true;
        }
        String title = job.getTitle() == null ? "" : job.getTitle().toLowerCase();
        return criteria.getRequiredKeywords().stream()
                .anyMatch(keyword -> title.contains(keyword.toLowerCase()));
    }

    private boolean matchesKeywords(Job job) {
        if (criteria.getKeywords() == null || criteria.getKeywords().isEmpty()) {
            return true;
        }

        String searchable = buildSearchableText(job);

        return criteria.getKeywords().stream()
                .anyMatch(keyword -> searchable.contains(keyword.toLowerCase()));
    }

    private boolean matchesExcludeKeywords(Job job) {
        if (criteria.getExcludeKeywords() == null || criteria.getExcludeKeywords().isEmpty()) {
            return false;
        }

        String searchable = buildSearchableText(job);

        return criteria.getExcludeKeywords().stream()
                .anyMatch(keyword -> searchable.contains(keyword.toLowerCase()));
    }

    private boolean matchesLocation(Job job) {
        if (criteria.getLocations() == null || criteria.getLocations().isEmpty()) {
            return true;
        }
        if (job.getLocation() == null) {
            return false;
        }

        String jobLocation = job.getLocation().toLowerCase();
        return criteria.getLocations().stream()
                .anyMatch(loc -> jobLocation.contains(loc.toLowerCase()));
    }

    private boolean matchesMinSalary(Job job) {
        if (criteria.getMinSalary() == null) {
            return true;
        }
        if (job.getSalaryMax() == null) {
            return true;
        }

        return job.getSalaryMax() >= criteria.getMinSalary();
    }

    private String buildSearchableText(Job job) {
        StringBuilder sb = new StringBuilder();
        if (job.getTitle() != null) sb.append(job.getTitle());
        sb.append(" ");
        if (job.getDescription() != null) sb.append(job.getDescription());
        sb.append(" ");
        if (job.getCompany() != null) sb.append(job.getCompany());
        return sb.toString().toLowerCase();
    }
}
