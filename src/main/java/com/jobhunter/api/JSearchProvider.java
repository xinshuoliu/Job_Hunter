package com.jobhunter.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jobhunter.http.Http;
import com.jobhunter.model.Job;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class JSearchProvider implements JobProvider {

    private static final Logger log = LoggerFactory.getLogger(JSearchProvider.class);
    static final String DEFAULT_BASE_URL = "https://jsearch.p.rapidapi.com/search-v2";

    private final OkHttpClient client;
    private final String baseUrl;
    private final String apiKey;
    private final String querySuffix;

    public JSearchProvider(String apiKey) {
        this(apiKey, "");
    }

    public JSearchProvider(String apiKey, String querySuffix) {
        this(Http.client(), DEFAULT_BASE_URL, apiKey, querySuffix);
    }

    JSearchProvider(OkHttpClient client, String baseUrl, String apiKey, String querySuffix) {
        this.client = client;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.querySuffix = querySuffix;
    }

    @Override
    public String getName() { return "jsearch"; }

    @Override
    public int getMaxPages() { return 1; }

    @Override
    public List<Job> fetchJobs(String query, String location, int page) throws IOException {
        String fullQuery = query + " intern " + location + (querySuffix.isEmpty() ? "" : " " + querySuffix);
        HttpUrl url = HttpUrl.parse(baseUrl).newBuilder()
                .addQueryParameter("query", fullQuery)
                .addQueryParameter("page", String.valueOf(page))
                .addQueryParameter("num_pages", "1")
                .addQueryParameter("date_posted", "month")
                .addQueryParameter("country", "ca")
                .build();

        log.info("Fetching from JSearch (page {})", page);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("X-RapidAPI-Key", apiKey)
                .addHeader("X-RapidAPI-Host", "jsearch.p.rapidapi.com")
                .build();

        try (Response response = client.newCall(request).execute()) {
            String body = response.body().string();
            if (!response.isSuccessful()) {
                log.error("JSearch API returned {}: {}", response.code(), body);
                throw new IOException("JSearch API error: " + response.code());
            }
            return parseResponse(body);
        }
    }

    private List<Job> parseResponse(String json) {
        List<Job> jobs = new ArrayList<>();
        JsonObject root = JsonParser.parseString(json).getAsJsonObject();

        JsonElement dataEl = root.get("data");
        if (dataEl == null) return jobs;

        JsonArray data;
        if (dataEl.isJsonArray()) {
            data = dataEl.getAsJsonArray();
        } else if (dataEl.isJsonObject() && dataEl.getAsJsonObject().has("jobs")) {
            data = dataEl.getAsJsonObject().getAsJsonArray("jobs");
        } else {
            log.error("JSearch unexpected response structure: {}", json.substring(0, Math.min(300, json.length())));
            return jobs;
        }

        for (JsonElement element : data) {
            try {
                JsonObject obj = element.getAsJsonObject();
                Job job = new Job();

                job.setId("jsearch-" + getStringOrNull(obj, "job_id"));
                job.setSource("jsearch");
                job.setTitle(getStringOrNull(obj, "job_title"));
                job.setCompany(getStringOrNull(obj, "employer_name"));
                job.setDescription(getStringOrNull(obj, "job_description"));
                job.setUrl(getStringOrNull(obj, "job_apply_link"));

                String city = getStringOrNull(obj, "job_city");
                String country = getStringOrNull(obj, "job_country");
                if (city != null && country != null) job.setLocation(city + ", " + country);
                else if (city != null) job.setLocation(city);
                else if (country != null) job.setLocation(country);

                if (obj.has("job_min_salary") && !obj.get("job_min_salary").isJsonNull()) {
                    job.setSalaryMin(obj.get("job_min_salary").getAsDouble());
                }
                if (obj.has("job_max_salary") && !obj.get("job_max_salary").isJsonNull()) {
                    job.setSalaryMax(obj.get("job_max_salary").getAsDouble());
                }

                if (obj.has("job_posted_at_timestamp") && !obj.get("job_posted_at_timestamp").isJsonNull()) {
                    job.setPostedAt(Instant.ofEpochSecond(obj.get("job_posted_at_timestamp").getAsLong()));
                }

                jobs.add(job);
            } catch (Exception e) {
                log.warn("Failed to parse JSearch entry: {}", e.getMessage());
            }
        }

        log.info("Parsed {} jobs from JSearch", jobs.size());
        return jobs;
    }

    private String getStringOrNull(JsonObject obj, String key) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : null;
    }
}
