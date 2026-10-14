package com.jobhunter.api;

import com.jobhunter.model.Job;
import com.jobhunter.util.Text;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class LocationScopedProvider implements JobProvider {

    private static final Logger log = LoggerFactory.getLogger(LocationScopedProvider.class);

    private final JobProvider delegate;
    private final List<String> locations;

    public LocationScopedProvider(JobProvider delegate, List<String> locations) {
        this.delegate = delegate;
        this.locations = List.copyOf(locations);
    }

    public static JobProvider scope(JobProvider provider, List<String> locations) {
        return locations == null || locations.isEmpty() ? provider : new LocationScopedProvider(provider, locations);
    }

    @Override
    public String getName() { return delegate.getName(); }

    @Override
    public int getMaxPages() { return delegate.getMaxPages(); }

    @Override
    public int getIntervalHours() { return delegate.getIntervalHours(); }

    @Override
    public List<Job> fetchJobs(String query, String location, int page) throws IOException {
        List<Job> jobs = delegate.fetchJobs(query, location, page);
        List<Job> scoped = jobs.stream().filter(this::inScope).toList();
        log.info("{}: {} of {} postings are in the configured board locations", getName(), scoped.size(), jobs.size());
        return scoped;
    }

    private boolean inScope(Job job) {
        String normalized = Text.normalize(job.getLocation());
        return locations.stream().anyMatch(loc -> Text.containsTerm(normalized, loc));
    }
}
