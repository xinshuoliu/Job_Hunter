package com.jobhunter.filter;

import com.jobhunter.model.FilterCriteria;
import com.jobhunter.model.FilterCriteria.ExcludeScope;
import com.jobhunter.model.Job;
import com.jobhunter.util.Text;
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
        if (isEmpty(criteria.getRequiredKeywords())) {
            return true;
        }
        return containsAny(Text.normalize(job.getTitle()), criteria.getRequiredKeywords());
    }

    private boolean matchesKeywords(Job job) {
        if (isEmpty(criteria.getKeywords())) {
            return true;
        }
        return containsAny(buildSearchableText(job), criteria.getKeywords());
    }

    private boolean matchesExcludeKeywords(Job job) {
        if (isEmpty(criteria.getExcludeKeywords())) {
            return false;
        }
        String searchable = criteria.getExcludeScope() == ExcludeScope.ALL
                ? buildSearchableText(job)
                : Text.normalize(job.getTitle());
        return containsAny(searchable, criteria.getExcludeKeywords());
    }

    private boolean matchesLocation(Job job) {
        if (isEmpty(criteria.getLocations())) {
            return true;
        }
        if (job.getLocation() == null) {
            return false;
        }
        return containsAny(Text.normalize(job.getLocation()), criteria.getLocations());
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

    private static boolean containsAny(String normalizedText, List<String> terms) {
        return terms.stream().anyMatch(term -> Text.containsTerm(normalizedText, term));
    }

    private static boolean isEmpty(List<String> values) {
        return values == null || values.isEmpty();
    }

    private String buildSearchableText(Job job) {
        return Text.normalize(String.join(" ",
                nullToEmpty(job.getTitle()),
                nullToEmpty(job.getDescription()),
                nullToEmpty(job.getCompany())));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
