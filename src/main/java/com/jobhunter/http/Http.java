package com.jobhunter.http;

import okhttp3.OkHttpClient;

import java.time.Duration;

public final class Http {

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(30))
            .writeTimeout(Duration.ofSeconds(20))
            .callTimeout(Duration.ofSeconds(120))
            .addInterceptor(new RetryInterceptor(3, 1_000, 30_000))
            .build();

    private Http() {
    }

    public static OkHttpClient client() {
        return CLIENT;
    }
}
