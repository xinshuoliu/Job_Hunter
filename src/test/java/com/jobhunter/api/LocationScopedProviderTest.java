package com.jobhunter.api;

import com.jobhunter.model.Job;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LocationScopedProviderTest {

    private final JobProvider source = new JobProvider() {
        @Override
        public String getName() { return "simplify"; }

        @Override
        public int getMaxPages() { return 1; }

        @Override
        public int getIntervalHours() { return 3; }

        @Override
        public List<Job> fetchJobs(String query, String location, int page) {
            return List.of(
                    job("1", "Montréal, QC, Canada"),
                    job("2", "Toronto, ON, Canada"),
                    job("3", "Remote in Canada"),
                    job("4", "Remote in USA"),
                    job("5", null));
        }
    };

    @Test
    @DisplayName("Only postings in the configured locations are kept")
    void keepsOnlyScopedLocations() throws IOException {
        JobProvider scoped = LocationScopedProvider.scope(source, List.of("Montreal", "Remote in Canada"));

        List<String> ids = scoped.fetchJobs("", "", 1).stream().map(Job::getId).toList();

        assertEquals(List.of("1", "3"), ids);
        assertEquals("simplify", scoped.getName());
        assertEquals(3, scoped.getIntervalHours());
        assertEquals(1, scoped.getMaxPages());
    }

    @Test
    @DisplayName("An empty location list leaves the provider untouched")
    void emptyScopeReturnsSameProvider() {
        assertSame(source, LocationScopedProvider.scope(source, List.of()));
        assertSame(source, LocationScopedProvider.scope(source, null));
    }

    private static Job job(String id, String location) {
        Job job = new Job();
        job.setId(id);
        job.setTitle("Software Intern");
        job.setLocation(location);
        return job;
    }
}
