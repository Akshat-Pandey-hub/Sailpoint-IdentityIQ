package com.keyforge.iiq.client;

import com.keyforge.iiq.config.AppConfig;

import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.StringJoiner;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An {@link IiqApiClient} that establishes a full IdentityIQ <b>web session</b> before
 * issuing requests, for the classic UI endpoints (the Group Configuration data sources
 * under {@code /define/groups/...}) that reject HTTP Basic auth and require a logged-in
 * session cookie.
 *
 * <p>Login is the standard IdentityIQ 8.4 <b>JSF form postback</b> (verified live on
 * this instance): GET {@code login.jsf} to seed a {@code JSESSIONID} and read the
 * {@code javax.faces.ViewState} token, then POST {@code login.jsf} with the
 * {@code loginForm} fields (username, password, button, and the echoed ViewState).
 * The old container-managed {@code j_security_check} action returns 404 here and is
 * not used.
 *
 * <p>An in-memory {@link CookieManager} captures the (httpOnly) session cookies on
 * login and replays them on every subsequent call; the {@code CSRF-TOKEN} cookie, when
 * present, is echoed back as the {@code X-XSRF-TOKEN} header to match the browser's
 * requests exactly. It reuses the same {@link AppConfig} settings (IIQ_BASE_URL /
 * IIQ_USERNAME / IIQ_PASSWORD — never hard-coded); no browser, no manual cookies, no
 * export. A data call that comes back as a login redirect is surfaced as an error
 * rather than being read as an empty result.
 */
public class IiqSessionClient extends IiqApiClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(60);
    private static final int MAX_ERROR_BODY = 2000;

    /** IdentityIQ 8.4 JSF login page — GET to seed the session, POST to authenticate. */
    static final String LOGIN_PATH = "login.jsf";
    /** Referer the browser sends for the Group Configuration data-source requests. */
    static final String GROUPS_REFERER_PATH = "define/groups/groups.jsf";
    /** Any authenticated UI page; loading it makes IIQ set the session-wide CSRF-TOKEN cookie. */
    static final String HOME_PATH = "home.jsf";

    // login form field names (from the live IdentityIQ 8.4 loginForm)
    static final String FORM_MARKER = "loginForm";
    static final String FIELD_USERNAME = "loginForm:accountId";
    static final String FIELD_PASSWORD = "loginForm:password";
    static final String FIELD_BUTTON = "loginForm:loginButton";
    static final String FIELD_TIMEZONE = "loginForm:initialTimeZoneId";
    static final String FIELD_PRELOGIN = "loginForm:preLoginUrl";
    static final String FIELD_VIEWSTATE = "javax.faces.ViewState";
    static final String LOGIN_BUTTON_LABEL = "Login";

    private final String baseUrl;
    private final String username;
    private final String password;
    private final HttpClient httpClient;
    private final CookieManager cookieManager;
    private final boolean debug;

    private volatile boolean authenticated;
    /** CSRF token parsed from an authenticated UI page (IIQ embeds it in JS, not a Set-Cookie). */
    private volatile String csrfTokenValue;

    public IiqSessionClient(AppConfig config) {
        this(config, false);
    }

    /** @param debug when true, logs each request's method/URL/status (never credentials) to stderr. */
    public IiqSessionClient(AppConfig config, boolean debug) {
        super(); // no Basic-auth header; this client authenticates via a JSF session
        this.baseUrl = config.getBaseUrl();
        this.username = config.getUsername();
        this.password = config.getPassword();
        this.debug = debug;

        this.cookieManager = new CookieManager();
        this.cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .cookieHandler(cookieManager)
                // Handle the login redirect ourselves so we can detect auth failures.
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    /** Test seam: inject a fake HttpClient and endpoints via the package-private ctor. */
    IiqSessionClient(String baseUrl, String username, String password, HttpClient httpClient) {
        super();
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;
        this.httpClient = httpClient;
        this.cookieManager = null;
        this.debug = false;
    }

    /**
     * Performs an authenticated {@code GET}. The IdentityIQ web session is established
     * on first use and reused afterwards.
     *
     * @throws IiqApiException on transport failure, a non-2xx status, or a login
     *         redirect (which means the session was not accepted)
     */
    @Override
    public String get(String path, Map<String, String> queryParams) {
        authenticate();

        URI uri = buildUri(path, queryParams);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", baseUrl + "/" + GROUPS_REFERER_PATH);
        // Echo the CSRF cookie as the browser does; harmless when the server doesn't require it.
        String csrf = csrfToken();
        if (csrf != null) {
            builder.header("X-XSRF-TOKEN", csrf);
        }
        HttpResponse<String> response = send(builder.GET().build(), uri);

        int status = response.statusCode();
        if (debug) {
            System.err.println("[IIQ] GET " + uri + " -> HTTP " + status);
            if (status < 200 || status >= 300) {
                System.err.println("[IIQ]   Location: "
                        + response.headers().firstValue("Location").orElse("<none>"));
                System.err.println("[IIQ]   body: " + truncate(response.body()));
            }
        }
        if (isRedirect(status)) {
            throw new IiqApiException("IdentityIQ redirected " + uri + " to "
                    + response.headers().firstValue("Location").orElse("<login>")
                    + " — the authenticated session was not accepted (HTTP " + status + ").", status);
        }
        if (status < 200 || status >= 300) {
            throw new IiqApiException("IdentityIQ request to " + uri + " failed with HTTP "
                    + status + ": " + truncate(response.body()), status);
        }
        return response.body();
    }

    /**
     * Performs an authenticated JSON POST to a classic UI REST endpoint (e.g.
     * {@code ui/rest/workItems/}). Establishes the session on first use, warms up the
     * CSRF token the way the browser does (a UI page load sets the CSRF-TOKEN cookie),
     * and echoes it in the {@code X-XSRF-TOKEN} header — exactly as the captured request.
     */
    @Override
    public String postJson(String path, String jsonBody) {
        authenticate();
        ensureCsrfToken();

        URI uri = buildUri(path, null);
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Accept", "application/json, text/plain, */*")
                .header("Content-Type", "application/json;charset=UTF-8")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Referer", baseUrl + "/" + HOME_PATH)
                .POST(HttpRequest.BodyPublishers.ofString(
                        jsonBody == null ? "" : jsonBody, StandardCharsets.UTF_8));
        String csrf = csrfToken();
        if (csrf != null) {
            builder.header("X-XSRF-TOKEN", csrf); // required by IIQ for POST
        }

        HttpResponse<String> response = send(builder.build(), uri);
        int status = response.statusCode();
        if (debug) {
            System.err.println("[IIQ] POST " + uri + " -> HTTP " + status);
            if (status < 200 || status >= 300) {
                System.err.println("[IIQ]   body: " + truncate(response.body()));
            }
        }
        if (isRedirect(status)) {
            throw new IiqApiException("IdentityIQ redirected POST " + uri + " to "
                    + response.headers().firstValue("Location").orElse("<login>")
                    + " — the authenticated session was not accepted (HTTP " + status + ").", status);
        }
        if (status < 200 || status >= 300) {
            throw new IiqApiException("IdentityIQ POST to " + uri + " failed with HTTP "
                    + status + ": " + truncate(response.body()), status);
        }
        return response.body();
    }

    /**
     * Performs an authenticated {@code application/x-www-form-urlencoded} POST — the classic
     * IdentityIQ JSF postback used by UI actions (e.g. opening the Edit Workgroup page, which
     * sets the session's "current workgroup" that {@code workgroupMembersDataSource.json} then
     * reads). If the postback answers with a redirect (as JSF navigation does), the redirect is
     * followed with a GET so the target page renders and primes the session; that page's body is
     * returned. Additive: existing {@link #get}/{@link #postJson}/{@link #authenticate} are unchanged.
     */
    public String postForm(String path, Map<String, String> formFields) {
        authenticate();

        URI uri = buildUri(path, null);
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", baseUrl + "/" + GROUPS_REFERER_PATH)
                .POST(HttpRequest.BodyPublishers.ofString(
                        encodeForm(formFields == null ? Map.of() : formFields), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = send(request, uri);
        int status = response.statusCode();
        if (debug) {
            System.err.println("[IIQ] POST(form) " + uri + " -> HTTP " + status
                    + " (Location: " + response.headers().firstValue("Location").orElse("<none>") + ")");
        }
        if (isRedirect(status)) {
            String location = response.headers().firstValue("Location").orElse(null);
            if (location != null && !location.isBlank()) {
                URI target = uri.resolve(location);
                HttpResponse<String> followed = send(HttpRequest.newBuilder(target)
                        .timeout(REQUEST_TIMEOUT).header("Accept", "text/html").GET().build(), target);
                if (debug) {
                    System.err.println("[IIQ]   -> follow GET " + target + " -> HTTP " + followed.statusCode());
                }
                return followed.body();
            }
        }
        if (status < 200 || status >= 400) {
            throw new IiqApiException("IdentityIQ form POST to " + uri + " failed with HTTP "
                    + status + ": " + truncate(response.body()), status);
        }
        return response.body();
    }

    /**
     * Warms the session's CSRF token so a subsequent {@link #get} includes the
     * {@code X-XSRF-TOKEN} header. Some classic {@code ui/rest} GET endpoints (e.g.
     * {@code ui/rest/identityRequests}) require the token even for reads and redirect to
     * login without it. Additive: existing {@code get}/{@code postJson}/{@code postForm}/
     * {@code authenticate} are unchanged; this just triggers the same token acquisition
     * {@code postJson} already performs.
     */
    public void warmCsrfToken() {
        authenticate();
        ensureCsrfToken();
    }

    /**
     * Obtains the CSRF token the way the browser does — except we can't run JavaScript,
     * so instead of relying on the {@code document.cookie = 'CSRF-TOKEN=...'} that IIQ
     * embeds in an authenticated page, we fetch {@code home.jsf} and parse that token
     * out of the HTML. IIQ validates the {@code X-XSRF-TOKEN} header against the token
     * bound to the session, so the parsed value is exactly what the POST needs.
     */
    private void ensureCsrfToken() {
        if (csrfToken() != null) {
            return;
        }
        URI page = buildUri(HOME_PATH, null);
        try {
            HttpResponse<String> resp = send(HttpRequest.newBuilder(page)
                    .timeout(REQUEST_TIMEOUT).header("Accept", "text/html").GET().build(), page);
            this.csrfTokenValue = extractCsrfToken(resp.body());
            if (debug) {
                System.err.println("[IIQ] GET " + page + " -> HTTP " + resp.statusCode()
                        + " (CSRF token " + (csrfTokenValue == null ? "NOT found" : "acquired") + ")");
            }
        } catch (IiqApiException warmUpFailure) {
            // Non-fatal: the POST below surfaces the real error if the token is truly missing.
        }
    }

    /**
     * Parses the CSRF token from an IIQ page's embedded JS, e.g.
     * {@code var cookieDef = 'CSRF-TOKEN=<token>; path=/identityiq; samesite=lax';}.
     */
    static String extractCsrfToken(String html) {
        if (html == null) {
            return null;
        }
        Matcher m = Pattern.compile("CSRF-TOKEN=([^;'\"\\s]+)").matcher(html);
        return m.find() ? m.group(1).trim() : null;
    }

    /** Establishes the IdentityIQ web session once via the JSF login postback (idempotent). */
    public synchronized void authenticate() {
        if (authenticated) {
            return;
        }

        // 1. GET login.jsf: seed the JSESSIONID cookie and read the JSF ViewState token.
        URI loginUri = buildUri(LOGIN_PATH, null);
        HttpResponse<String> page = send(HttpRequest.newBuilder(loginUri)
                .timeout(REQUEST_TIMEOUT).GET().build(), loginUri);
        if (debug) {
            System.err.println("[IIQ] GET " + loginUri + " -> HTTP " + page.statusCode() + " (login seed)");
        }
        String html = page.body();
        String viewState = extractHiddenValue(html, FIELD_VIEWSTATE);
        if (viewState == null) {
            throw new IiqApiException("Could not find '" + FIELD_VIEWSTATE + "' on " + loginUri
                    + " — the login page is not the expected IdentityIQ 8.4 JSF form.");
        }
        // The timezone hidden field is filled client-side by the browser; supply the JVM default.
        String timeZone = firstNonBlank(extractHiddenValue(html, FIELD_TIMEZONE), TimeZone.getDefault().getID());
        String preLoginUrl = firstNonBlank(extractHiddenValue(html, FIELD_PRELOGIN), "");

        // 2. POST login.jsf with the loginForm fields (credentials are form-encoded, never logged).
        Map<String, String> form = new LinkedHashMap<>();
        form.put(FORM_MARKER, FORM_MARKER);
        form.put(FIELD_TIMEZONE, timeZone);
        form.put(FIELD_PRELOGIN, preLoginUrl);
        form.put(FIELD_USERNAME, username);
        form.put(FIELD_PASSWORD, password);
        form.put(FIELD_BUTTON, LOGIN_BUTTON_LABEL);
        form.put(FIELD_VIEWSTATE, viewState);

        HttpRequest loginRequest = HttpRequest.newBuilder(loginUri)
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Referer", loginUri.toString())
                .POST(HttpRequest.BodyPublishers.ofString(encodeForm(form), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = send(loginRequest, loginUri);
        int status = response.statusCode();
        String location = response.headers().firstValue("Location").orElse("");
        if (debug) {
            System.err.println("[IIQ] POST " + loginUri + " -> HTTP " + status
                    + " (Location: " + (location.isEmpty() ? "<none>" : location) + ")");
        }

        // Login is REJECTED when IdentityIQ redirects back to the login page, or answers
        // 2xx while re-rendering the login form (bad credentials). Anything else that is a
        // normal transport result (a redirect to home, or an app page) means the session is
        // now authenticated — and a session that is not really authenticated is caught
        // loudly on the first data call anyway (login redirect / 404).
        boolean redirectedToLogin = location.toLowerCase().contains(LOGIN_PATH);
        boolean stillOnLoginPage = status >= 200 && status < 300
                && response.body() != null && response.body().contains("name=\"" + FIELD_PASSWORD + "\"");
        boolean transportOk = status >= 200 && status < 400;
        if (redirectedToLogin || stillOnLoginPage || !transportOk) {
            throw new IiqApiException("IdentityIQ login failed for user '" + username + "' (HTTP "
                    + status + (location.isEmpty() ? "" : ", redirect " + location)
                    + ") — check IIQ_USERNAME / IIQ_PASSWORD.", status);
        }
        authenticated = true;
    }

    // --- helpers ------------------------------------------------------------

    /** The CSRF token: the value parsed from a UI page, or a {@code CSRF-TOKEN} cookie if present. */
    private String csrfToken() {
        if (csrfTokenValue != null) {
            return csrfTokenValue;
        }
        if (cookieManager == null) {
            return null;
        }
        for (HttpCookie cookie : cookieManager.getCookieStore().getCookies()) {
            if ("CSRF-TOKEN".equalsIgnoreCase(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /** Reads a hidden-input value from the login HTML, tolerant of attribute order. */
    static String extractHiddenValue(String html, String fieldName) {
        if (html == null) {
            return null;
        }
        String name = Pattern.quote(fieldName);
        Matcher m = Pattern.compile("name=\"" + name + "\"[^>]*?value=\"([^\"]*)\"").matcher(html);
        if (m.find()) {
            return m.group(1);
        }
        m = Pattern.compile("value=\"([^\"]*)\"[^>]*?name=\"" + name + "\"").matcher(html);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private static String encodeForm(Map<String, String> fields) {
        StringJoiner body = new StringJoiner("&");
        for (Map.Entry<String, String> e : fields.entrySet()) {
            body.add(encode(e.getKey()) + "=" + encode(e.getValue()));
        }
        return body.toString();
    }

    private HttpResponse<String> send(HttpRequest request, URI uri) {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (java.io.IOException e) {
            throw new IiqApiException("I/O error calling IdentityIQ at " + uri, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IiqApiException("Interrupted while calling IdentityIQ at " + uri, e);
        }
    }

    private URI buildUri(String path, Map<String, String> queryParams) {
        String normalised = path == null ? "" : path.trim();
        while (normalised.startsWith("/")) {
            normalised = normalised.substring(1);
        }
        StringBuilder url = new StringBuilder(baseUrl).append('/').append(normalised);
        if (queryParams != null && !queryParams.isEmpty()) {
            StringJoiner query = new StringJoiner("&", "?", "");
            for (Map.Entry<String, String> e : new LinkedHashMap<>(queryParams).entrySet()) {
                query.add(encode(e.getKey()) + "=" + encode(e.getValue()));
            }
            url.append(query);
        }
        try {
            return URI.create(url.toString());
        } catch (IllegalArgumentException e) {
            throw new IiqApiException("Could not build a valid request URL for path '" + path + "'", e);
        }
    }

    private static boolean isRedirect(int status) {
        return status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b;
    }

    private static String truncate(String body) {
        if (body == null) {
            return "<no body>";
        }
        return body.length() <= MAX_ERROR_BODY ? body : body.substring(0, MAX_ERROR_BODY) + "... (truncated)";
    }
}
