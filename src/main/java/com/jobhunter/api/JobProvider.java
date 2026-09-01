package com.jobhunter.api;

import com.jobhunter.model.Job;
import java.io.IOException;
import java.util.List;

public interface JobProvider {

    String getName();

    List<Job> fetchJobs(String query, String location, int page) throws IOException;

    default int getMaxPages() { return 5; }

    default int getIntervalHours() { return 6; }
}
