package com.jobhunter.api;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jobhunter.model.Job;
import com.jobhunter.util.Text;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public abstract class CompanyBoardProvider implements JobProvider {

    private static final Logger log = LoggerFactory.getLogger(CompanyBoardProvider.class);

    private final OkHttpClient client;
    private final Map<String, String> companies;

    protected CompanyBoardProvider(OkHttpClient client, Map<String, String> companies) {
        this.client = client;
        this.companies = new LinkedHashMap<>(companies);
    }

    protected abstract String boardUrl(String company);

    protected abstract List<Job> parse(String json, String company, String displayName);

    @Override
    public int getMaxPages() { return 1; }

    @Override
    public List<Job> fetchJobs(String query, String location, int page) throws IOException {
        if (page > 1) return List.of();

        List<Job> jobs = new ArrayList<>();
        int failures = 0;

        for (Map.Entry<String, String> entry : companies.entrySet()) {
            String company = entry.getKey();
            try {
                List<Job> fromCompany = parse(get(boardUrl(company)), company, entry.getValue());
                log.info("Fetched {} postings from {} board '{}'", fromCompany.size(), getName(), company);
                jobs.addAll(fromCompany);
            } catch (Exception e) {
                failures++;
                log.warn("Failed to fetch {} board '{}': {}", getName(), company, e.getMessage());
            }
        }

        if (!companies.isEmpty() && failures == companies.size()) {
            throw new IOException("Every " + getName() + " board failed");
        }
        return jobs;
    }

    private String get(String url) throws IOException {
        Request request = new Request.Builder().url(url).build();
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                throw new IOException("HTTP " + response.code());
            }
            return response.body().string();
        }
    }

    protected static String companyName(String configured, String fromApi, String slug) {
        if (configured != null && !configured.isBlank()) return configured;
        if (fromApi != null && !fromApi.isBlank()) return fromApi.trim();
        return Text.titleCase(slug.replace('-', ' '));
    }

    protected static String getString(JsonObject obj, String key) {
        JsonElement value = obj.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    protected static boolean getBoolean(JsonObject obj, String key, boolean fallback) {
        JsonElement value = obj.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsBoolean() : fallback;
    }

    protected static JsonObject getObject(JsonObject obj, String key) {
        JsonElement value = obj.get(key);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }
}
