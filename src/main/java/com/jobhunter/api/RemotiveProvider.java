package com.jobhunter.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jobhunter.http.Http;
import com.jobhunter.model.Job;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class RemotiveProvider implements JobProvider {

    private static final Logger log = LoggerFactory.getLogger(RemotiveProvider.class);
    static final String DEFAULT_BASE_URL = "https://remotive.com/api/remote-jobs?category=software-dev&limit=100";

    private final OkHttpClient client;
    private final String baseUrl;

    public RemotiveProvider() {
        this(Http.client(), DEFAULT_BASE_URL);
    }

    RemotiveProvider(OkHttpClient client, String baseUrl) {
        this.client = client;
        this.baseUrl = baseUrl;
    }

    @Override
    public String getName() { return "remotive"; }

    @Override
    public int getMaxPages() { return 1; }

    @Override
    public List<Job> fetchJobs(String query, String location, int page) throws IOException {
        if (page > 1) return List.of();

        log.info("Fetching from Remotive: {}", baseUrl);

        Request request = new Request.Builder().url(baseUrl).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("Remotive API returned {}: {}", response.code(), response.message());
                throw new IOException("Remotive API error: " + response.code());
            }
            return parseResponse(response.body().string());
        }
    }

    private List<Job> parseResponse(String json) {
        List<Job> jobs = new ArrayList<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray jobsArray = root.getAsJsonArray("jobs");

        if (jobsArray == null) return jobs;

        for (JsonElement element : jobsArray) {
            try {
                JsonObject obj = element.getAsJsonObject();
                Job job = new Job();

                job.setId("remotive-" + obj.get("id").getAsString());
                job.setSource("remotive");
                job.setTitle(getStringOrNull(obj, "title"));
                job.setCompany(getStringOrNull(obj, "company_name"));
                job.setDescription(getStringOrNull(obj, "description"));
                job.setUrl(getStringOrNull(obj, "url"));
                job.setLocation(getStringOrNull(obj, "candidate_required_location"));

                if (obj.has("publication_date") && !obj.get("publication_date").isJsonNull()) {
                    try {
                        job.setPostedAt(Instant.parse(obj.get("publication_date").getAsString().replace(" ", "T") + "Z"));
                    } catch (DateTimeParseException e) {
                        job.setPostedAt(Instant.now());
                    }
                }

                jobs.add(job);
            } catch (Exception e) {
                log.warn("Failed to parse Remotive entry: {}", e.getMessage());
            }
        }

        log.info("Parsed {} jobs from Remotive", jobs.size());
        return jobs;
    }

    private String getStringOrNull(JsonObject obj, String key) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : null;
    }
}
