package com.keyforge.iiq.client;

import com.keyforge.iiq.config.AppConfig;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Thin, generic HTTP client for the IdentityIQ REST/SCIM API.
 *
 * <p>Built on the JDK's {@link java.net.http.HttpClient}. It knows how to:
 * <ul>
 *     <li>build request URLs relative to the configured base URL,</li>
 *     <li>attach HTTP Basic Authentication,</li>
 *     <li>perform authenticated {@code GET} requests, and</li>
 *     <li>turn non-2xx responses into a meaningful {@link IiqApiException}.</li>
 * </ul>
 *
 * <p>The class is intentionally endpoint-agnostic so that later services
 * (accounts, applications, entitlements, ...) can reuse it. Credentials are
 * never logged or placed into exception messages.
 *
 * <p>The {@link #get(String, Map)} method is non-final so tests can supply
 * canned responses without hitting a live server.
 */
public class IiqApiClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);

    /** Max chars of a failing response body echoed into an exception message. */
    private static final int MAX_ERROR_BODY = 2000;

    private final String baseUrl;
    private final String authorizationHeader;
    private final HttpClient httpClient;

    public IiqApiClient(AppConfig config) {
        this(config, HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    /** Constructor that accepts an explicit {@link HttpClient}, useful for tuning or testing. */
    public IiqApiClient(AppConfig config, HttpClient httpClient) {
        this.baseUrl = config.getBaseUrl();
        this.authorizationHeader = buildBasicAuthHeader(config.getUsername(), config.getPassword());
        this.httpClient = httpClient;
    }

    /**
     * No-arg constructor for subclasses / test doubles that fully override
     * {@link #get(String, Map)} and therefore need no live HTTP client.
     */
    protected IiqApiClient() {
        this.baseUrl = null;
        this.authorizationHeader = null;
        this.httpClient = null;
    }

    /**
     * Performs an authenticated {@code GET} against a path relative to the base URL.
     *
     * @param path        path relative to the base URL, e.g. {@code "scim/v2/Users"}
     *                    (a leading slash is tolerated)
     * @param queryParams query parameters (may be {@code null} or empty); values are URL-encoded
     * @return the response body as a string
     * @throws IiqApiException on any transport failure or a non-2xx status code
     */
    public String get(String path, Map<String, String> queryParams) {
        URI uri = buildUri(path, queryParams);

        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", authorizationHeader)
                // IIQ SCIM returns application/scim+json; accept plain JSON too.
                .header("Accept", "application/scim+json, application/json")
                .GET()
                .build();

        HttpResponse<String> response;
        try {
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new IiqApiException("I/O error calling IdentityIQ at " + safeUri(uri), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IiqApiException("Interrupted while calling IdentityIQ at " + safeUri(uri), e);
        }

        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IiqApiException(
                    "IdentityIQ request to " + safeUri(uri) + " failed with HTTP " + status
                    + ": " + truncate(response.body()),
                    status);
        }
        return response.body();
    }

    /**
     * Performs an authenticated JSON {@code POST}. The base SCIM client is GET-only;
     * the classic UI REST endpoints (e.g. {@code ui/rest/workItems/}) require a POST
     * with a session cookie + CSRF token, which {@link IiqSessionClient} provides by
     * overriding this method. Overridable so tests can supply canned responses.
     *
     * @param path     path relative to the base URL
     * @param jsonBody the request body (already-serialised JSON)
     * @return the response body as a string
     */
    public String postJson(String path, String jsonBody) {
        throw new UnsupportedOperationException(
                "POST is not supported by this client; use IiqSessionClient for UI REST endpoints.");
    }

    // --- helpers ------------------------------------------------------------

    private URI buildUri(String path, Map<String, String> queryParams) {
        String normalisedPath = path == null ? "" : path.trim();
        while (normalisedPath.startsWith("/")) {
            normalisedPath = normalisedPath.substring(1);
        }

        StringBuilder url = new StringBuilder(baseUrl).append('/').append(normalisedPath);

        if (queryParams != null && !queryParams.isEmpty()) {
            StringJoiner query = new StringJoiner("&", "?", "");
            // Preserve insertion order for predictable, testable URLs.
            Map<String, String> ordered = new LinkedHashMap<>(queryParams);
            for (Map.Entry<String, String> e : ordered.entrySet()) {
                query.add(encode(e.getKey()) + "=" + encode(e.getValue()));
            }
            url.append(query);
        }

        try {
            return URI.create(url.toString());
        } catch (IllegalArgumentException e) {
            throw new IiqApiException("Could not build a valid request URL from base '"
                    + baseUrl + "' and path '" + path + "'", e);
        }
    }

    private static String buildBasicAuthHeader(String username, String password) {
        String raw = username + ":" + password;
        String encoded = Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
        return "Basic " + encoded;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** URI never carries credentials (Basic auth lives in a header), so it is safe to log. */
    private static String safeUri(URI uri) {
        return uri.toString();
    }

    private static String truncate(String body) {
        if (body == null) {
            return "<no body>";
        }
        if (body.length() <= MAX_ERROR_BODY) {
            return body;
        }
        return body.substring(0, MAX_ERROR_BODY) + "... (truncated)";
    }
}
