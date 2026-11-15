package com.expensetracker.suggest;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

/** Minimal HTTP POST abstraction, so the LLM client can be tested without a network. */
public interface HttpTransport {

    /** Status code, body and the Retry-After header (in seconds) if the server sent one. */
    record Response(int status, String body, Optional<Long> retryAfterSeconds) {
    }

    Response post(URI uri, Map<String, String> headers, String body, Duration timeout)
            throws IOException, InterruptedException;

    /** Real implementation on the JDK's built-in HTTP client (no extra dependency). */
    static HttpTransport jdk() {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        return (uri, headers, body, timeout) -> {
            HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            headers.forEach(request::header);
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            Optional<Long> retryAfter = response.headers().firstValue("retry-after").flatMap(value -> {
                try {
                    return Optional.of(Long.parseLong(value.trim()));
                } catch (NumberFormatException e) {
                    return Optional.empty(); // HTTP-date form: ignore and use our own backoff
                }
            });
            return new Response(response.statusCode(), response.body(), retryAfter);
        };
    }
}
