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
import java.util.ArrayList;
import java.util.List;

public class ArbeitnowProvider implements JobProvider {

    private static final Logger log = LoggerFactory.getLogger(ArbeitnowProvider.class);
    private static final String BASE_URL = "https://www.arbeitnow.com/api/job-board-api";

    private final OkHttpClient client;

    public ArbeitnowProvider() {
        this.client = Http.client();
    }

    @Override
    public String getName() {
        return "arbeitnow";
    }

    @Override
    public List<Job> fetchJobs(String query, String location, int page) throws IOException {
        String url = BASE_URL + "?page=" + page;

        log.info("Fetching from Arbeitnow: {}", url);

        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("Arbeitnow API returned {}: {}", response.code(), response.message());
                throw new IOException("Arbeitnow API error: " + response.code());
            }

            String body = response.body().string();
            return parseResponse(body, query, location);
        }
    }

    private List<Job> parseResponse(String json, String query, String location) {
        List<Job> jobs = new ArrayList<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray data = root.getAsJsonArray("data");

        if (data == null) return jobs;

        String queryLower = query == null ? "" : query.toLowerCase();
        String locationLower = location == null ? "" : location.toLowerCase();

        for (JsonElement element : data) {
            try {
                JsonObject obj = element.getAsJsonObject();
                Job job = new Job();

                job.setId("arbeitnow-" + obj.get("slug").getAsString());
                job.setSource("arbeitnow");
                job.setTitle(getStringOrNull(obj, "title"));
                job.setCompany(getStringOrNull(obj, "company_name"));
                job.setLocation(getStringOrNull(obj, "location"));
                job.setDescription(getStringOrNull(obj, "description"));
                job.setUrl(getStringOrNull(obj, "url"));

                if (obj.has("created_at") && !obj.get("created_at").isJsonNull()) {
                    job.setPostedAt(Instant.ofEpochSecond(obj.get("created_at").getAsLong()));
                }

                if (matchesFilter(job, queryLower, locationLower)) {
                    jobs.add(job);
                }
            } catch (Exception e) {
                log.warn("Failed to parse Arbeitnow entry: {}", e.getMessage());
            }
        }

        log.info("Parsed {} matching jobs from Arbeitnow", jobs.size());
        return jobs;
    }

    private boolean matchesFilter(Job job, String query, String location) {
        if (!location.isEmpty() && job.getLocation() != null) {
            if (!job.getLocation().toLowerCase().contains(location)) return false;
        }
        return true;
    }

    private String getStringOrNull(JsonObject obj, String key) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : null;
    }
}
