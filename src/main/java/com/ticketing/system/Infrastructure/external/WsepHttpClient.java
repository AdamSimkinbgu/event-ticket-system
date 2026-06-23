package com.ticketing.system.Infrastructure.external;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Thin HTTP client for the WSEP external-systems endpoint. Every operation is a
 * single {@code application/x-www-form-urlencoded} POST to one URL, dispatched
 * by the {@code action_type} field; the server replies with a tiny plain-text
 * body where {@code "-1"} is the universal failure sentinel (callers interpret
 * the body).
 *
 * <p>Shared by {@link WsepPaymentGateway} and {@link WsepTicketIssuer}. Always a
 * bean (it is lightweight) even when the in-process stubs are the active
 * provider — the adapters that use it are property-gated, not this helper.
 */
@Component
public class WsepHttpClient {

    private final String baseUrl;
    private final HttpClient http;

    public WsepHttpClient(@Value("${wsep.base-url:https://damp-lynna-wsep-1984852e.koyeb.app/}") String baseUrl) {
        this.baseUrl = baseUrl;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** Starts an ordered form body with {@code action_type} first. */
    public static Form action(String actionType) {
        return new Form().add("action_type", actionType);
    }

    /** A tiny ordered form builder (insertion order preserved for readable request logs). */
    public static final class Form {
        private final Map<String, String> fields = new LinkedHashMap<>();

        public Form add(String key, Object value) {
            fields.put(key, value == null ? "" : String.valueOf(value));
            return this;
        }

        private String encoded() {
            return fields.entrySet().stream()
                    .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                    .collect(Collectors.joining("&"));
        }

        private static String enc(String s) {
            return URLEncoder.encode(s, StandardCharsets.UTF_8);
        }
    }

    /**
     * POSTs the form and returns the trimmed plain-text body. Throws
     * {@link WsepCommunicationException} on any transport-level failure, so a
     * caller can tell "couldn't reach WSEP" apart from a {@code "-1"} reply.
     */
    public String post(Form form) {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form.encoded(), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.body() == null ? "" : response.body().trim();
        } catch (IOException e) {
            throw new WsepCommunicationException("WSEP request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WsepCommunicationException("WSEP request interrupted", e);
        }
    }
}
