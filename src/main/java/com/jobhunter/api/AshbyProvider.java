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

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AshbyProvider extends CompanyBoardProvider {

    private static final Logger log = LoggerFactory.getLogger(AshbyProvider.class);
    private static final String BASE_URL = "https://api.ashbyhq.com/posting-api/job-board/";

    public AshbyProvider(Map<String, String> companies) {
        this(Http.client(), companies);
    }

    AshbyProvider(OkHttpClient client, Map<String, String> companies) {
        super(client, companies);
    }

    @Override
    public String getName() { return "ashby"; }

    @Override
    protected String boardUrl(String company) {
        return BASE_URL + company;
    }

    @Override
    protected List<Job> parse(String json, String company, String displayName) {
        List<Job> jobs = new ArrayList<>();
        JsonArray array = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("jobs");
        if (array == null) return jobs;

        for (JsonElement element : array) {
            try {
                JsonObject obj = element.getAsJsonObject();
                if (!getBoolean(obj, "isListed", true)) continue;

                Job job = new Job();
                job.setId("ashby-" + company + "-" + getString(obj, "id"));
                job.setSource("ashby");
                job.setTitle(Text.cleanTitle(getString(obj, "title")));
                job.setCompany(companyName(displayName, null, company));
                job.setLocation(location(obj));
                job.setDescription(getString(obj, "descriptionPlain"));
                job.setUrl(getString(obj, "jobUrl"));

                String published = getString(obj, "publishedAt");
                if (published != null) job.setPostedAt(OffsetDateTime.parse(published).toInstant());

                jobs.add(job);
            } catch (Exception e) {
                log.warn("Failed to parse Ashby posting for '{}': {}", company, e.getMessage());
            }
        }
        return jobs;
    }

    private static String location(JsonObject obj) {
        List<String> parts = new ArrayList<>();
        if (getString(obj, "location") != null) parts.add(getString(obj, "location"));

        JsonElement secondary = obj.get("secondaryLocations");
        if (secondary != null && secondary.isJsonArray()) {
            for (JsonElement loc : secondary.getAsJsonArray()) {
                if (loc.isJsonObject() && getString(loc.getAsJsonObject(), "location") != null) {
                    parts.add(getString(loc.getAsJsonObject(), "location"));
                }
            }
        }
        if (getBoolean(obj, "isRemote", false)) {
            parts.add("Remote");
        }
        return parts.isEmpty() ? null : String.join("; ", parts);
    }
}
