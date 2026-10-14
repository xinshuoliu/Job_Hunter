package com.jobhunter.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.stream.JsonReader;
import com.jobhunter.http.Http;
import com.jobhunter.model.Job;
import com.jobhunter.util.Text;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class SimplifyProvider implements JobProvider {

    private static final Logger log = LoggerFactory.getLogger(SimplifyProvider.class);

    private final OkHttpClient client;
    private final List<String> urls;
    private final int maxAgeDays;

    public SimplifyProvider(List<String> urls, int maxAgeDays) {
        this(Http.client(), urls, maxAgeDays);
    }

    SimplifyProvider(OkHttpClient client, List<String> urls, int maxAgeDays) {
        this.client = client;
        this.urls = List.copyOf(urls);
        this.maxAgeDays = maxAgeDays;
    }

    @Override
    public String getName() { return "simplify"; }

    @Override
    public int getMaxPages() { return 1; }

    @Override
    public List<Job> fetchJobs(String query, String location, int page) throws IOException {
        if (page > 1) return List.of();

        List<Job> jobs = new ArrayList<>();
        int failures = 0;

        for (String url : urls) {
            Request request = new Request.Builder().url(url).build();
            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new IOException("HTTP " + response.code());
                }
                List<Job> fromList = parse(response.body().charStream(), Instant.now());
                log.info("Parsed {} active listings from {}", fromList.size(), url);
                jobs.addAll(fromList);
            } catch (Exception e) {
                failures++;
                log.warn("Failed to fetch Simplify listings from {}: {}", url, e.getMessage());
            }
        }

        if (!urls.isEmpty() && failures == urls.size()) {
            throw new IOException("Every Simplify listing source failed");
        }
        return jobs;
    }

    List<Job> parse(Reader json, Instant now) throws IOException {
        Instant cutoff = maxAgeDays > 0 ? now.minus(maxAgeDays, ChronoUnit.DAYS) : Instant.MIN;
        List<Job> jobs = new ArrayList<>();

        try (JsonReader reader = new JsonReader(json)) {
            reader.beginArray();
            while (reader.hasNext()) {
                JsonElement element = JsonParser.parseReader(reader);
                try {
                    Job job = toJob(element.getAsJsonObject());
                    if (job != null && (job.getPostedAt() == null || !job.getPostedAt().isBefore(cutoff))) {
                        jobs.add(job);
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse Simplify listing: {}", e.getMessage());
                }
            }
            reader.endArray();
        }
        return jobs;
    }

    private Job toJob(JsonObject obj) {
        if (!flag(obj, "active") || !flag(obj, "is_visible")) return null;

        Job job = new Job();
        job.setId("simplify-" + string(obj, "id"));
        job.setSource("simplify");
        job.setTitle(Text.cleanTitle(string(obj, "title")));
        job.setCompany(string(obj, "company_name"));
        job.setLocation(join(obj.get("locations"), "; "));
        job.setUrl(string(obj, "url"));

        List<String> details = new ArrayList<>();
        if (string(obj, "category") != null) details.add(string(obj, "category"));
        String terms = join(obj.get("terms"), ", ");
        if (terms != null) details.add(terms);
        job.setDescription(details.isEmpty() ? null : String.join(" | ", details));

        JsonElement posted = obj.get("date_posted");
        if (posted != null && posted.isJsonPrimitive()) {
            job.setPostedAt(Instant.ofEpochSecond(posted.getAsLong()));
        }
        return job;
    }

    private static boolean flag(JsonObject obj, String key) {
        JsonElement value = obj.get(key);
        return value == null || !value.isJsonPrimitive() || value.getAsBoolean();
    }

    private static String string(JsonObject obj, String key) {
        JsonElement value = obj.get(key);
        return value != null && value.isJsonPrimitive() ? value.getAsString() : null;
    }

    private static String join(JsonElement array, String separator) {
        if (array == null || !array.isJsonArray()) return null;
        List<String> parts = new ArrayList<>();
        for (JsonElement item : (JsonArray) array) {
            if (item.isJsonPrimitive()) parts.add(item.getAsString());
        }
        return parts.isEmpty() ? null : String.join(separator, parts);
    }
}
