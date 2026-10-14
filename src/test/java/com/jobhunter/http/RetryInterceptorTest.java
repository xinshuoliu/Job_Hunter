package com.jobhunter.http;

import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RetryInterceptorTest {

    private final List<Long> sleeps = new ArrayList<>();
    private MockWebServer server;
    private OkHttpClient client;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        client = new OkHttpClient.Builder()
                .addInterceptor(new RetryInterceptor(3, 100, 1_000, sleeps::add))
                .build();
    }

    @AfterEach
    void tearDown() {
        server.close();
    }

    @Test
    @DisplayName("Server errors are retried with exponential backoff until one succeeds")
    void retriesServerErrors() throws IOException {
        server.enqueue(new MockResponse.Builder().code(503).build());
        server.enqueue(new MockResponse.Builder().code(502).build());
        server.enqueue(new MockResponse.Builder().code(200).body("ok").build());

        try (Response response = get()) {
            assertEquals(200, response.code());
            assertEquals("ok", response.body().string());
        }
        assertEquals(3, server.getRequestCount());
        assertEquals(List.of(100L, 200L), sleeps);
    }

    @Test
    @DisplayName("Retry-After is honoured and capped")
    void honoursRetryAfter() throws IOException {
        server.enqueue(new MockResponse.Builder().code(429).addHeader("Retry-After", "0.4").build());
        server.enqueue(new MockResponse.Builder().code(429).addHeader("Retry-After", "120").build());
        server.enqueue(new MockResponse.Builder().code(200).build());

        try (Response response = get()) {
            assertEquals(200, response.code());
        }
        assertEquals(List.of(1_000L, 1_000L), sleeps, "0.4s rounds up to 1s; 120s is capped at the 1s maximum");
    }

    @Test
    @DisplayName("After the last attempt the error response is returned to the caller")
    void givesUpAfterMaxAttempts() throws IOException {
        for (int i = 0; i < 3; i++) server.enqueue(new MockResponse.Builder().code(500).build());

        try (Response response = get()) {
            assertEquals(500, response.code());
        }
        assertEquals(3, server.getRequestCount());
    }

    @Test
    @DisplayName("Client errors are not retried")
    void doesNotRetryClientErrors() throws IOException {
        server.enqueue(new MockResponse.Builder().code(404).build());

        try (Response response = get()) {
            assertEquals(404, response.code());
        }
        assertEquals(1, server.getRequestCount());
        assertTrue(sleeps.isEmpty());
    }

    @Test
    @DisplayName("POST requests are never retried, so webhooks are not sent twice")
    void doesNotRetryPosts() throws IOException {
        server.enqueue(new MockResponse.Builder().code(503).build());

        Request post = new Request.Builder()
                .url(server.url("/hook"))
                .post(RequestBody.create("{}", MediaType.parse("application/json")))
                .build();
        try (Response response = client.newCall(post).execute()) {
            assertEquals(503, response.code());
        }
        assertEquals(1, server.getRequestCount());
    }

    @Test
    @DisplayName("Backoff doubles per attempt and respects the cap")
    void backoffSchedule() {
        RetryInterceptor interceptor = new RetryInterceptor(10, 100, 1_000, millis -> { });
        assertEquals(100, interceptor.backoff(1));
        assertEquals(400, interceptor.backoff(3));
        assertEquals(1_000, interceptor.backoff(8));
    }

    private Response get() throws IOException {
        return client.newCall(new Request.Builder().url(server.url("/jobs")).build()).execute();
    }
}
