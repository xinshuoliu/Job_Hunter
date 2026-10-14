package com.jobhunter.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.jobhunter.http.Http;
import com.jobhunter.model.Job;
import com.jobhunter.util.Text;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class LeverProvider extends CompanyBoardProvider {

    private static final Logger log = LoggerFactory.getLogger(LeverProvider.class);
    static final String DEFAULT_BASE_URL = "https://api.lever.co/v0/postings/";
    private final String baseUrl;

    public LeverProvider(Map<String, String> companies) {
        this(Http.client(), companies, DEFAULT_BASE_URL);
    }

    LeverProvider(OkHttpClient client, Map<String, String> companies, String baseUrl) {
        super(client, companies);
        this.baseUrl = baseUrl;
    }

    @Override
    public String getName() { return "lever"; }

    @Override
    protected String boardUrl(String company) {
        return baseUrl + company + "?mode=json";
    }

    @Override
    protected List<Job> parse(String json, String company, String displayName) {
        List<Job> jobs = new ArrayList<>();
        JsonArray array = JsonParser.parseString(json).getAsJsonArray();

        for (JsonElement element : array) {
            try {
                JsonObject obj = element.getAsJsonObject();
                Job job = new Job();
                job.setId("lever-" + company + "-" + getString(obj, "id"));
                job.setSource("lever");
                job.setTitle(Text.cleanTitle(getString(obj, "text")));
                job.setCompany(companyName(displayName, null, company));
                job.setLocation(location(obj));
                job.setDescription(getString(obj, "descriptionPlain"));
                job.setUrl(getString(obj, "hostedUrl"));

                if (obj.has("createdAt") && obj.get("createdAt").isJsonPrimitive()) {
                    job.setPostedAt(Instant.ofEpochMilli(obj.get("createdAt").getAsLong()));
                }

                JsonObject salary = getObject(obj, "salaryRange");
                if (salary != null && "per-year-salary".equals(getString(salary, "interval"))) {
                    if (salary.has("min")) job.setSalaryMin(salary.get("min").getAsDouble());
                    if (salary.has("max")) job.setSalaryMax(salary.get("max").getAsDouble());
                    job.setCurrency(getString(salary, "currency"));
                }

                jobs.add(job);
            } catch (Exception e) {
                log.warn("Failed to parse Lever posting for '{}': {}", company, e.getMessage());
            }
        }
        return jobs;
    }

    private static String location(JsonObject obj) {
        JsonObject categories = getObject(obj, "categories");
        if (categories == null) return null;

        List<String> parts = new ArrayList<>();
        JsonElement all = categories.get("allLocations");
        if (all != null && all.isJsonArray()) {
            all.getAsJsonArray().forEach(loc -> parts.add(loc.getAsString()));
        } else if (getString(categories, "location") != null) {
            parts.add(getString(categories, "location"));
        }
        if ("remote".equalsIgnoreCase(getString(obj, "workplaceType"))) {
            parts.add("Remote");
        }
        return parts.isEmpty() ? null : String.join("; ", parts);
    }
}
