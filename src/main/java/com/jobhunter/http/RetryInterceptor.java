package com.jobhunter.http;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.OptionalLong;
import java.util.Set;

public class RetryInterceptor implements Interceptor {

    private static final Logger log = LoggerFactory.getLogger(RetryInterceptor.class);

    private static final Set<Integer> RETRYABLE_STATUS = Set.of(429, 500, 502, 503, 504);
    private static final Set<String> IDEMPOTENT_METHODS = Set.of("GET", "HEAD");

    @FunctionalInterface
    public interface Sleeper {
        void sleep(long millis) throws InterruptedException;
    }

    private final int maxAttempts;
    private final long baseDelayMillis;
    private final long maxDelayMillis;
    private final Sleeper sleeper;

    public RetryInterceptor(int maxAttempts, long baseDelayMillis, long maxDelayMillis) {
        this(maxAttempts, baseDelayMillis, maxDelayMillis, Thread::sleep);
    }

    public RetryInterceptor(int maxAttempts, long baseDelayMillis, long maxDelayMillis, Sleeper sleeper) {
        this.maxAttempts = Math.max(1, maxAttempts);
        this.baseDelayMillis = baseDelayMillis;
        this.maxDelayMillis = maxDelayMillis;
        this.sleeper = sleeper;
    }

    @Override
    public Response intercept(Chain chain) throws IOException {
        Request request = chain.request();
        if (!IDEMPOTENT_METHODS.contains(request.method())) {
            return chain.proceed(request);
        }

        for (int attempt = 1; ; attempt++) {
            Response response;
            try {
                response = chain.proceed(request);
            } catch (InterruptedIOException e) {
                throw e;
            } catch (IOException e) {
                if (attempt >= maxAttempts) throw e;
                long wait = backoff(attempt);
                log.warn("{} {} failed ({}), retrying in {} ms (attempt {}/{})",
                        request.method(), request.url().host(), e.getMessage(), wait, attempt, maxAttempts);
                pause(wait);
                continue;
            }

            if (!RETRYABLE_STATUS.contains(response.code()) || attempt >= maxAttempts) {
                return response;
            }

            long wait = retryAfter(response).orElse(backoff(attempt));
            log.warn("{} {} returned {}, retrying in {} ms (attempt {}/{})",
                    request.method(), request.url().host(), response.code(), wait, attempt, maxAttempts);
            response.close();
            pause(wait);
        }
    }

    long backoff(int attempt) {
        long delay = baseDelayMillis << Math.min(attempt - 1, 20);
        return Math.min(delay, maxDelayMillis);
    }

    private OptionalLong retryAfter(Response response) {
        String header = response.header("Retry-After");
        if (header == null) return OptionalLong.empty();
        try {
            long seconds = (long) Math.ceil(Double.parseDouble(header.trim()));
            return OptionalLong.of(Math.min(Math.max(0, seconds) * 1000, maxDelayMillis));
        } catch (NumberFormatException e) {
            return OptionalLong.empty();
        }
    }

    private void pause(long millis) throws IOException {
        try {
            sleeper.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while waiting to retry");
        }
    }
}
