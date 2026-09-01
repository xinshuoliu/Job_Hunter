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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

public class AdzunaProvider implements JobProvider {

    private static final Logger log = LoggerFactory.getLogger(AdzunaProvider.class);
    private static final String BASE_URL = "https://api.adzuna.com/v1/api/jobs";

    private final OkHttpClient client;
    private final String appId;
    private final String appKey;
    private final String country;

    public AdzunaProvider(String appId, String appKey, String country) {
        this.client = Http.client();
        this.appId = appId;
        this.appKey = appKey;
        this.country = country;
    }

    @Override
    public String getName() { return "adzuna"; }

    @Override
    public int getMaxPages() { return 2; }

    @Override
    public List<Job> fetchJobs(String query, String location, int page) throws IOException {
        String url = String.format(
                "%s/%s/search/%d?app_id=%s&app_key=%s&what=%s&where=%s&results_per_page=20&sort_by=date",
                BASE_URL, country, page, appId, appKey,
                encodeQuery(query), encodeQuery(location)
        );

        log.info("Fetching from Adzuna (page {})", page);

        Request request = new Request.Builder().url(url).build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("Adzuna API returned {}: {}", response.code(), response.message());
                throw new IOException("Adzuna API error: " + response.code());
            }

            String body = response.body().string();
            return parseResponse(body);
        }
    }

    private List<Job> parseResponse(String json) {
        List<Job> jobs = new ArrayList<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonArray results = root.getAsJsonArray("results");

        if (results == null) return jobs;

        for (JsonElement element : results) {
            try {
                JsonObject obj = element.getAsJsonObject();
                Job job = new Job();

                job.setId("adzuna-" + obj.get("id").getAsString());
                job.setSource("adzuna");
                job.setTitle(getStringOrNull(obj, "title"));
                job.setDescription(getStringOrNull(obj, "description"));
                job.setUrl(getStringOrNull(obj, "redirect_url"));

                if (obj.has("company") && !obj.get("company").isJsonNull()) {
                    job.setCompany(getStringOrNull(obj.getAsJsonObject("company"), "display_name"));
                }

                if (obj.has("location") && !obj.get("location").isJsonNull()) {
                    job.setLocation(getStringOrNull(obj.getAsJsonObject("location"), "display_name"));
                }

                if (obj.has("salary_min") && !obj.get("salary_min").isJsonNull()) {
                    job.setSalaryMin(obj.get("salary_min").getAsDouble());
                }
                if (obj.has("salary_max") && !obj.get("salary_max").isJsonNull()) {
                    job.setSalaryMax(obj.get("salary_max").getAsDouble());
                }

                if (obj.has("created") && !obj.get("created").isJsonNull()) {
                    job.setPostedAt(parseDate(obj.get("created").getAsString()));
                }

                jobs.add(job);
            } catch (Exception e) {
                log.warn("Failed to parse job entry: {}", e.getMessage());
            }
        }

        log.info("Parsed {} jobs from Adzuna", jobs.size());
        return jobs;
    }

    private Instant parseDate(String dateStr) {
        try {
            return Instant.parse(dateStr);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                        .toInstant(ZoneOffset.UTC);
            } catch (DateTimeParseException e2) {
                return Instant.now();
            }
        }
    }

    private String getStringOrNull(JsonObject obj, String key) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : null;
    }

    private String encodeQuery(String query) {
        return query == null ? "" : URLEncoder.encode(query, StandardCharsets.UTF_8);
    }
}
