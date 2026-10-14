package com.jobhunter.api;

import com.jobhunter.model.Job;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ProviderHttpTest {

    private final OkHttpClient client = new OkHttpClient();
    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    @DisplayName("Adzuna sends credentials and search terms and parses the results")
    void adzuna() throws Exception {
        server.enqueue(json(Fixtures.read("adzuna.json")));
        AdzunaProvider provider = new AdzunaProvider(client, base("v1/api/jobs"), "id1", "key1", "ca");

        List<Job> jobs = provider.fetchJobs("software developer", "Montreal", 2);

        RecordedRequest request = server.takeRequest();
        assertTrue(request.getTarget().startsWith("/v1/api/jobs/ca/search/2?"), request.getTarget());
        assertEquals("id1", request.getUrl().queryParameter("app_id"));
        assertEquals("key1", request.getUrl().queryParameter("app_key"));
        assertEquals("software developer", request.getUrl().queryParameter("what"));
        assertEquals("Montreal", request.getUrl().queryParameter("where"));

        assertEquals(2, jobs.size());
        Job matrox = jobs.get(0);
        assertEquals("adzuna-5722019816", matrox.getId());
        assertEquals("Matrox", matrox.getCompany());
        assertEquals("Roxboro, Montréal", matrox.getLocation());
        assertEquals(52000.0, matrox.getSalaryMax());
        assertEquals(Instant.parse("2026-09-12T14:03:11Z"), matrox.getPostedAt());
        assertEquals(Instant.parse("2026-09-25T08:00:00Z"), jobs.get(1).getPostedAt());
    }

    @Test
    @DisplayName("JSearch sends the RapidAPI key and parses city and country")
    void jsearch() throws Exception {
        server.enqueue(json(Fixtures.read("jsearch.json")));
        JSearchProvider provider = new JSearchProvider(client, base("search-v2"), "secret", "linkedin");

        List<Job> jobs = provider.fetchJobs("software developer", "Montreal", 1);

        RecordedRequest request = server.takeRequest();
        assertEquals("secret", request.getHeaders().get("X-RapidAPI-Key"));
        assertEquals("software developer intern Montreal linkedin", request.getUrl().queryParameter("query"));

        Job job = jobs.get(0);
        assertEquals("jsearch-abc123==", job.getId());
        assertEquals("Montreal, CA", job.getLocation());
        assertNull(job.getSalaryMax());
        assertEquals(Instant.ofEpochSecond(1791849600L), job.getPostedAt());
    }

    @Test
    @DisplayName("Arbeitnow keeps only postings in the searched location")
    void arbeitnow() throws Exception {
        server.enqueue(json(Fixtures.read("arbeitnow.json")));
        ArbeitnowProvider provider = new ArbeitnowProvider(client, base("api/job-board-api"));

        List<Job> jobs = provider.fetchJobs("developer", "Montreal", 3);

        assertEquals("3", server.takeRequest().getUrl().queryParameter("page"));
        assertEquals(List.of("arbeitnow-python-intern-montreal-456"), jobs.stream().map(Job::getId).toList());
    }

    @Test
    @DisplayName("Remotive parses its local publication timestamp as UTC")
    void remotive() throws Exception {
        server.enqueue(json(Fixtures.read("remotive.json")));
        RemotiveProvider provider = new RemotiveProvider(client, base("api/remote-jobs"));

        List<Job> jobs = provider.fetchJobs("", "", 1);

        assertEquals(1, jobs.size());
        assertEquals("remotive-2091001", jobs.get(0).getId());
        assertEquals("Canada", jobs.get(0).getLocation());
        assertEquals(Instant.parse("2026-10-01T12:30:00Z"), jobs.get(0).getPostedAt());
        assertTrue(provider.fetchJobs("", "", 2).isEmpty(), "Remotive has a single page");
    }

    @Test
    @DisplayName("A failing search API surfaces as an IOException")
    void searchApiErrorsThrow() {
        server.enqueue(new MockResponse.Builder().code(401).body("{\"message\":\"bad key\"}").build());
        AdzunaProvider provider = new AdzunaProvider(client, base("v1/api/jobs"), "id", "wrong", "ca");

        IOException error = assertThrows(IOException.class, () -> provider.fetchJobs("dev", "Montreal", 1));
        assertTrue(error.getMessage().contains("401"));
    }

    @Test
    @DisplayName("One broken company board does not stop the others")
    void boardsTolerateSingleFailures() throws Exception {
        server.enqueue(new MockResponse.Builder().code(404).body("{\"ok\":false}").build());
        server.enqueue(json(Fixtures.read("lever.json")));
        Map<String, String> companies = new LinkedHashMap<>();
        companies.put("gone", "");
        companies.put("mirego", "");
        LeverProvider provider = new LeverProvider(client, companies, base(""));

        List<Job> jobs = provider.fetchJobs("", "", 1);

        assertEquals("/gone?mode=json", server.takeRequest().getTarget());
        assertEquals("/mirego?mode=json", server.takeRequest().getTarget());
        assertEquals(2, jobs.size());
        assertEquals("Mirego", jobs.get(0).getCompany());
    }

    @Test
    @DisplayName("When every company board fails the provider reports an error")
    void boardsFailWhenAllFail() {
        server.enqueue(new MockResponse.Builder().code(500).build());
        server.enqueue(new MockResponse.Builder().code(404).build());
        Map<String, String> companies = new LinkedHashMap<>();
        companies.put("a", "");
        companies.put("b", "");
        GreenhouseProvider provider = new GreenhouseProvider(client, companies, base(""));

        assertThrows(IOException.class, () -> provider.fetchJobs("", "", 1));
    }

    @Test
    @DisplayName("Ashby and Greenhouse request the right board URLs")
    void boardUrls() throws Exception {
        server.enqueue(json(Fixtures.read("ashby.json")));
        server.enqueue(json(Fixtures.read("greenhouse.json")));

        new AshbyProvider(client, Map.of("valence", "Valence Labs"), base("posting-api/job-board/")).fetchJobs("", "", 1);
        new GreenhouseProvider(client, Map.of("workleap", ""), base("v1/boards/")).fetchJobs("", "", 1);

        assertEquals("/posting-api/job-board/valence", server.takeRequest().getTarget());
        assertEquals("/v1/boards/workleap/jobs?content=true", server.takeRequest().getTarget());
    }

    @Test
    @DisplayName("Simplify streams the listing file over HTTP")
    void simplifyOverHttp() throws Exception {
        server.enqueue(json(Fixtures.read("simplify.json")));
        SimplifyProvider provider = new SimplifyProvider(client, List.of(base("listings.json")), 0);

        List<Job> jobs = provider.fetchJobs("", "", 1);

        assertEquals("/listings.json", server.takeRequest().getTarget());
        assertEquals(2, jobs.size());
    }

    private String base(String path) {
        return server.url("/" + path).toString();
    }

    private static MockResponse json(String body) {
        return new MockResponse.Builder()
                .addHeader("Content-Type", "application/json; charset=utf-8")
                .body(body)
                .build();
    }
}
