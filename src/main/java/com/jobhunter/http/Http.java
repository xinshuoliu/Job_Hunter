package com.jobhunter.http;

import okhttp3.OkHttpClient;

import java.time.Duration;

public final class Http {

    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(10))
            .readTimeout(Duration.ofSeconds(20))
            .writeTimeout(Duration.ofSeconds(20))
            .callTimeout(Duration.ofSeconds(45))
            .build();

    private Http() {
    }

    public static OkHttpClient client() {
        return CLIENT;
    }
}
