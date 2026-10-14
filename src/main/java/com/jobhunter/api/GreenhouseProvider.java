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

public class GreenhouseProvider extends CompanyBoardProvider {

    private static final Logger log = LoggerFactory.getLogger(GreenhouseProvider.class);
    private static final String BASE_URL = "https://boards-api.greenhouse.io/v1/boards/";

    public GreenhouseProvider(Map<String, String> companies) {
        this(Http.client(), companies);
    }

    GreenhouseProvider(OkHttpClient client, Map<String, String> companies) {
        super(client, companies);
    }

    @Override
    public String getName() { return "greenhouse"; }

    @Override
    protected String boardUrl(String company) {
        return BASE_URL + company + "/jobs?content=true";
    }

    @Override
    protected List<Job> parse(String json, String company, String displayName) {
        List<Job> jobs = new ArrayList<>();
        JsonArray array = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("jobs");
        if (array == null) return jobs;

        for (JsonElement element : array) {
            try {
                JsonObject obj = element.getAsJsonObject();
                Job job = new Job();
                job.setId("greenhouse-" + company + "-" + getString(obj, "id"));
                job.setSource("greenhouse");
                job.setTitle(Text.cleanTitle(getString(obj, "title")));
                job.setCompany(companyName(displayName, getString(obj, "company_name"), company));
                JsonObject location = getObject(obj, "location");
                job.setLocation(location != null ? getString(location, "name") : null);
                job.setDescription(Text.stripHtml(getString(obj, "content")));
                job.setUrl(getString(obj, "absolute_url"));

                String published = getString(obj, "first_published");
                if (published == null) published = getString(obj, "updated_at");
                if (published != null) job.setPostedAt(OffsetDateTime.parse(published).toInstant());

                jobs.add(job);
            } catch (Exception e) {
                log.warn("Failed to parse Greenhouse posting for '{}': {}", company, e.getMessage());
            }
        }
        return jobs;
    }
}
