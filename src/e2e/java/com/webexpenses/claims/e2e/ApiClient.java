package com.webexpenses.claims.e2e;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * Thin HTTP client wrapper for E2E tests.
 * Uses JDK HttpClient — no Spring context, no magic.
 */
public class ApiClient {

    private final HttpClient client;
    private final String baseUrl;

    public record Response(int status, String body) {}

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl;
        this.client = HttpClient.newHttpClient();
    }

    public Response post(String path, String jsonBody, String token) {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody));

        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }

        return send(builder.build());
    }

    public Response get(String path, String token) {
        var builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .GET();

        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }

        return send(builder.build());
    }

    public Response patch(String path, String jsonBody, String token) {
        var bodyPublisher = jsonBody != null
                ? HttpRequest.BodyPublishers.ofString(jsonBody)
                : HttpRequest.BodyPublishers.noBody();

        var builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .method("PATCH", bodyPublisher);

        if (token != null) {
            builder.header("Authorization", "Bearer " + token);
        }

        return send(builder.build());
    }

    private Response send(HttpRequest request) {
        try {
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new Response(response.statusCode(), response.body());
        } catch (Exception e) {
            throw new RuntimeException("HTTP request failed: " + request.uri(), e);
        }
    }
}
