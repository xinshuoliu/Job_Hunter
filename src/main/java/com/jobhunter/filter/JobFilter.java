package com.jobhunter.filter;

import com.jobhunter.model.FilterCriteria;
import com.jobhunter.model.FilterCriteria.ExcludeScope;
import com.jobhunter.model.Job;
import com.jobhunter.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JobFilter {

    private static final Logger log = LoggerFactory.getLogger(JobFilter.class);
    private static final Pattern YEAR = Pattern.compile("\\b(20\\d\\d)\\b");

    private final FilterCriteria criteria;
    private final Clock clock;

    public JobFilter(FilterCriteria criteria) {
        this(criteria, Clock.systemDefaultZone());
    }

    public JobFilter(FilterCriteria criteria, Clock clock) {
        this.criteria = criteria;
        this.clock = clock;
    }

    public boolean matches(Job job) {
        return matchesRequiredKeywords(job)
                && matchesKeywords(job)
                && !matchesExcludeKeywords(job)
                && !matchesExcludedCompany(job)
                && matchesLocation(job)
                && !namesExcludedLocation(job)
                && matchesMinSalary(job)
                && isFresh(job)
                && !targetsPastYear(job);
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

    private boolean matchesExcludedCompany(Job job) {
        if (isEmpty(criteria.getExcludeCompanies())) {
            return false;
        }
        return containsAny(Text.normalize(job.getCompany()), criteria.getExcludeCompanies());
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

    private boolean namesExcludedLocation(Job job) {
        if (isEmpty(criteria.getExcludeLocations())) {
            return false;
        }
        String title = Text.normalize(job.getTitle());
        if (!containsAny(title, criteria.getExcludeLocations())) {
            return false;
        }
        return isEmpty(criteria.getLocations()) || !containsAny(title, criteria.getLocations());
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

    private boolean isFresh(Job job) {
        Integer maxAge = criteria.getMaxAgeDays();
        if (maxAge == null || maxAge <= 0 || job.getPostedAt() == null) {
            return true;
        }
        Instant cutoff = clock.instant().minus(maxAge, ChronoUnit.DAYS);
        return !job.getPostedAt().isBefore(cutoff);
    }

    private boolean targetsPastYear(Job job) {
        if (!criteria.isRejectPastYears() || job.getTitle() == null) {
            return false;
        }
        int latest = 0;
        Matcher matcher = YEAR.matcher(job.getTitle());
        while (matcher.find()) {
            latest = Math.max(latest, Integer.parseInt(matcher.group(1)));
        }
        return latest > 0 && latest < LocalDate.now(clock).getYear();
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
