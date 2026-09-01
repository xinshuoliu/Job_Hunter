package com.jobhunter.notification;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.jobhunter.http.Http;
import com.jobhunter.model.Job;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class DiscordNotifier implements Notifier {

    private static final Logger log = LoggerFactory.getLogger(DiscordNotifier.class);
    private static final MediaType JSON = MediaType.parse("application/json");

    private static final int BATCH_SIZE = 5;
    private static final int MAX_ATTEMPTS = 3;
    private static final long BATCH_PAUSE_MS = 500;
    private static final long MAX_BACKOFF_MS = 30_000;

    private final OkHttpClient client;
    private final String webhookUrl;
    private final String mentionUserId;

    public DiscordNotifier(String webhookUrl) {
        this(webhookUrl, "");
    }

    public DiscordNotifier(String webhookUrl, String mentionUserId) {
        this.client = Http.client();
        this.webhookUrl = webhookUrl;
        this.mentionUserId = mentionUserId == null ? "" : mentionUserId.trim();
    }

    @Override
    public boolean notify(List<Job> jobs) {
        if (jobs.isEmpty()) return true;

        boolean allDelivered = true;

        for (int i = 0; i < jobs.size(); i += BATCH_SIZE) {
            List<Job> batch = jobs.subList(i, Math.min(i + BATCH_SIZE, jobs.size()));

            if (!post(buildBatchPayload(batch, i == 0 ? jobs.size() : -1))) {
                allDelivered = false;
            } else {
                log.info("Discord notification sent ({} jobs)", batch.size());
            }

            if (i + BATCH_SIZE < jobs.size() && !pause(BATCH_PAUSE_MS)) {
                return false;
            }
        }

        return allDelivered;
    }

    @Override
    public void notifyStatus(String message) {
        JsonObject payload = new JsonObject();
        payload.addProperty("content", message);
        post(payload.toString());
    }

    private String buildBatchPayload(List<Job> jobs, int totalCount) {
        JsonObject payload = new JsonObject();

        if (totalCount > 0) {
            String header = String.format("🔔 **%d new job(s) found!**", totalCount);
            payload.addProperty("content", mentionUserId.isEmpty()
                    ? header
                    : "<@" + mentionUserId + "> " + header);
        }

        JsonArray embeds = new JsonArray();
        for (Job job : jobs) {
            JsonObject embed = new JsonObject();
            embed.addProperty("title", truncate(job.getTitle(), 256));
            embed.addProperty("url", job.getUrl());
            embed.addProperty("color", 3447003);

            StringBuilder desc = new StringBuilder();
            if (job.getCompany() != null) desc.append("**Company:** ").append(job.getCompany()).append("\n");
            if (job.getLocation() != null) desc.append("**Location:** ").append(job.getLocation()).append("\n");
            if (job.getSalaryMin() != null || job.getSalaryMax() != null) {
                desc.append("**Salary:** ");
                if (job.getSalaryMin() != null) desc.append(String.format("$%.0f", job.getSalaryMin()));
                desc.append(" - ");
                if (job.getSalaryMax() != null) desc.append(String.format("$%.0f", job.getSalaryMax()));
                desc.append("\n");
            }
            desc.append("**Source:** ").append(job.getSource());
            if (job.getAdditionalUrls() != null && !job.getAdditionalUrls().isEmpty()) {
                desc.append("\n**Also on:** ");
                for (int i = 0; i < job.getAdditionalUrls().size(); i++) {
                    if (i > 0) desc.append(" · ");
                    desc.append("[link ").append(i + 2).append("](").append(job.getAdditionalUrls().get(i)).append(")");
                }
            }

            embed.addProperty("description", truncate(desc.toString(), 4096));
            embeds.add(embed);
        }

        payload.add("embeds", embeds);
        return payload.toString();
    }

    private boolean post(String payload) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            RequestBody body = RequestBody.create(payload, JSON);
            Request request = new Request.Builder().url(webhookUrl).post(body).build();

            try (Response response = client.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    return true;
                }

                if (response.code() == 429 && attempt < MAX_ATTEMPTS) {
                    long waitMs = retryAfterMillis(response);
                    log.warn("Discord rate limited, retrying in {} ms (attempt {}/{})", waitMs, attempt, MAX_ATTEMPTS);
                    if (!pause(waitMs)) return false;
                    continue;
                }

                log.error("Discord webhook failed: {} {}", response.code(), response.message());
                return false;

            } catch (IOException e) {
                log.error("Failed to send Discord notification: {}", e.getMessage());
                return false;
            }
        }
        return false;
    }

    private long retryAfterMillis(Response response) {
        String header = response.header("Retry-After");
        if (header != null) {
            try {
                return Math.min((long) (Double.parseDouble(header) * 1000), MAX_BACKOFF_MS);
            } catch (NumberFormatException ignored) {
                log.debug("Unparseable Retry-After header: {}", header);
            }
        }

        try {
            ResponseBody body = response.body();
            if (body != null) {
                JsonObject json = JsonParser.parseString(body.string()).getAsJsonObject();
                if (json.has("retry_after")) {
                    return Math.min((long) (json.get("retry_after").getAsDouble() * 1000), MAX_BACKOFF_MS);
                }
            }
        } catch (Exception ignored) {
            log.debug("Could not read retry_after from Discord response body");
        }

        return 1000;
    }

    private boolean pause(long millis) {
        try {
            Thread.sleep(millis);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() > maxLen ? s.substring(0, maxLen - 3) + "..." : s;
    }
}
