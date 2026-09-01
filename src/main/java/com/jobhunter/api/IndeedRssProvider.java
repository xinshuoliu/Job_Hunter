package com.jobhunter.api;

import com.jobhunter.http.Http;
import com.jobhunter.model.Job;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class IndeedRssProvider implements JobProvider {

    private static final Logger log = LoggerFactory.getLogger(IndeedRssProvider.class);
    private static final String BASE_URL = "https://ca.indeed.com/rss";
    private static final DateTimeFormatter RSS_DATE = DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);

    private final OkHttpClient client;

    public IndeedRssProvider() {
        this.client = Http.client();
    }

    @Override
    public String getName() { return "indeed"; }

    @Override
    public int getMaxPages() { return 2; }

    @Override
    public List<Job> fetchJobs(String query, String location, int page) throws IOException {
        int start = (page - 1) * 10;
        String url = String.format("%s?q=%s&l=%s&sort=date&fromage=14&start=%d",
                BASE_URL,
                URLEncoder.encode(query == null ? "" : query, StandardCharsets.UTF_8),
                URLEncoder.encode(location == null ? "" : location, StandardCharsets.UTF_8),
                start);

        log.info("Fetching from Indeed RSS (page {})", page);

        Request request = new Request.Builder()
                .url(url)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .addHeader("Accept", "application/rss+xml, application/xml, text/xml, */*")
                .addHeader("Accept-Language", "en-CA,en;q=0.9,fr-CA;q=0.8")
                .addHeader("Accept-Encoding", "gzip, deflate, br")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                log.error("Indeed RSS returned {}: {}", response.code(), response.message());
                throw new IOException("Indeed RSS error: " + response.code());
            }
            return parseRss(response.body().string(), location);
        }
    }

    private List<Job> parseRss(String xml, String location) {
        List<Job> jobs = new ArrayList<>();
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));

            NodeList items = doc.getElementsByTagName("item");
            for (int i = 0; i < items.getLength(); i++) {
                try {
                    Element item = (Element) items.item(i);
                    String rawTitle = getTagText(item, "title");
                    String link     = getTagText(item, "link");
                    String guid     = getTagText(item, "guid");
                    String desc     = getTagText(item, "description");
                    String pubDate  = getTagText(item, "pubDate");

                    String jobTitle = rawTitle;
                    String company  = null;
                    if (rawTitle != null) {
                        String[] parts = rawTitle.split(" - ");
                        jobTitle = parts[0].trim();
                        if (parts.length >= 2) company = parts[parts.length - 1].trim();
                    }

                    Job job = new Job();
                    job.setId("indeed-" + Math.abs((guid != null ? guid : link).hashCode()));
                    job.setSource("indeed");
                    job.setTitle(jobTitle);
                    job.setCompany(company);
                    job.setUrl(link != null ? link : guid);
                    job.setLocation(location);
                    job.setDescription(stripHtml(desc));

                    if (pubDate != null) {
                        try {
                            job.setPostedAt(ZonedDateTime.parse(pubDate, RSS_DATE).toInstant());
                        } catch (Exception e) {
                            job.setPostedAt(Instant.now());
                        }
                    }

                    jobs.add(job);
                } catch (Exception e) {
                    log.warn("Failed to parse Indeed RSS item: {}", e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Failed to parse Indeed RSS XML: {}", e.getMessage());
        }

        log.info("Parsed {} jobs from Indeed RSS", jobs.size());
        return jobs;
    }

    private String getTagText(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() > 0 ? nodes.item(0).getTextContent() : null;
    }

    private String stripHtml(String html) {
        if (html == null) return null;
        return html.replaceAll("<[^>]+>", "")
                .replaceAll("&amp;", "&").replaceAll("&lt;", "<")
                .replaceAll("&gt;", ">").replaceAll("&nbsp;", " ").trim();
    }
}
