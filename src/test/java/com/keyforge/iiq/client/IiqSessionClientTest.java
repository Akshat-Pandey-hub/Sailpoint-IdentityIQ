package com.keyforge.iiq.client;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises {@link IiqSessionClient}'s IdentityIQ 8.4 JSF-login session flow with an
 * injected fake {@link HttpClient} (no sockets): the client GETs {@code login.jsf} to
 * read the {@code javax.faces.ViewState}, POSTs the {@code loginForm} credentials, and
 * on success reads the protected data source; bad credentials (login page re-rendered)
 * must fail loudly instead of being read as an empty result.
 */
class IiqSessionClientTest {

    private static final String BASE = "http://iiq.example/identityiq";
    private static final String DATA_JSON =
            "{\"totalCount\":1,\"workgroups\":[{\"id\":\"wg-1\",\"name\":\"AdminCap\"}]}";

    /** The IdentityIQ 8.4 login form, trimmed to the inputs the client parses. */
    private static final String LOGIN_HTML = ""
            + "<form id=\"loginForm\" name=\"loginForm\" method=\"post\" action=\"/identityiq/login.jsf\">"
            + "<input type=\"hidden\" name=\"loginForm\" value=\"loginForm\" />"
            + "<input type=\"hidden\" name=\"loginForm:initialTimeZoneId\" id=\"loginForm:initialTimeZoneId\" value=\"\" />"
            + "<input type=\"hidden\" name=\"loginForm:preLoginUrl\" value=\"\" />"
            + "<input type=\"text\" name=\"loginForm:accountId\" />"
            + "<input type=\"password\" name=\"loginForm:password\" />"
            + "<input type=\"submit\" name=\"loginForm:loginButton\" value=\"Login\" />"
            + "<input type=\"hidden\" name=\"javax.faces.ViewState\" id=\"j_id1:javax.faces.ViewState:0\" "
            + "value=\"vs-token-123:456\" autocomplete=\"off\" />"
            + "</form>";

    /** A failed login re-renders the login page (still carries the password field) plus an error. */
    private static final String LOGIN_HTML_ERROR =
            LOGIN_HTML + "<span class=\"error\">Invalid username or password</span>";

    private IiqSessionClient client(boolean goodCredentials) {
        return new IiqSessionClient(BASE, "spadmin", "secret", new FakeHttpClient(goodCredentials));
    }

    @Test
    void authenticatesThenReadsProtectedDataSource() {
        String body = client(true).get("define/groups/workgroupsDataSource.json",
                Map.of("start", "0", "limit", "100"));
        assertTrue(body.contains("AdminCap"));
        assertTrue(body.contains("totalCount"));
    }

    @Test
    void badCredentialsFailLoudlyInsteadOfReturningLoginPage() {
        IiqApiException e = assertThrows(IiqApiException.class,
                () -> client(false).get("define/groups/workgroupsDataSource.json", Map.of()));
        assertTrue(e.getMessage().toLowerCase().contains("login")
                || e.getMessage().toLowerCase().contains("session"));
    }

    @Test
    void postsJsfLoginFormWithEchoedViewStateAndPaginatesDataSource() {
        FakeHttpClient fake = new FakeHttpClient(true);
        new IiqSessionClient(BASE, "spadmin", "secret", fake)
                .get("define/groups/populationsDataSource.json", Map.of("start", "0", "limit", "100", "page", "1"));

        // JSF field names URL-encode the colon (loginForm:accountId -> loginForm%3AaccountId).
        assertTrue(fake.loginPostBody.contains("loginForm%3AaccountId=spadmin"));
        assertTrue(fake.loginPostBody.contains("loginForm%3Apassword=secret"));
        assertTrue(fake.loginPostBody.contains("loginForm%3AloginButton=Login"));
        assertTrue(fake.loginPostBody.contains("javax.faces.ViewState=vs-token-123%3A456"));
        // It must NOT use the container-managed action that 404s on this instance.
        assertTrue(!fake.loginPostBody.contains("j_security_check"));

        assertTrue(fake.lastDataSourceUri.contains("start=0"));
        assertTrue(fake.lastDataSourceUri.contains("limit=100"));
        assertTrue(fake.lastDataSourceUri.contains("page=1"));
    }

    @Test
    void extractsCsrfTokenFromEmbeddedJsExactlyAsIiqReturnsIt() {
        // The real IIQ 8.4 home.jsf embeds the token in a JS cookie definition.
        String html = "<script>\n"
                + "  var cookieDef = 'CSRF-TOKEN=/ch70IPgH2RDwQ8b6bfrFmJopWVi004F31Pqdu8ue0E=; "
                + "path=/identityiq; samesite=lax';\n"
                + "  document.cookie = cookieDef;\n"
                + "</script>";
        assertEquals("/ch70IPgH2RDwQ8b6bfrFmJopWVi004F31Pqdu8ue0E=", IiqSessionClient.extractCsrfToken(html));
        assertNull(IiqSessionClient.extractCsrfToken("<html>no token here</html>"));
        assertNull(IiqSessionClient.extractCsrfToken(null));
    }

    // --- fake HttpClient (no network) ---------------------------------------

    private static final class FakeHttpClient extends HttpClient {
        private final boolean goodCredentials;
        String loginPostBody = "";
        String lastDataSourceUri = "";

        FakeHttpClient(boolean goodCredentials) {
            this.goodCredentials = goodCredentials;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> handler) {
            String path = request.uri().getPath();
            if (path.endsWith("/login.jsf")) {
                if ("POST".equalsIgnoreCase(request.method())) {
                    loginPostBody = bodyOf(request);
                    return goodCredentials
                            ? resp(302, "", BASE + "/home.jsf")
                            : resp(200, LOGIN_HTML_ERROR, null);
                }
                return resp(200, LOGIN_HTML, null); // GET seed page
            }
            if (path.endsWith("DataSource.json")) {
                lastDataSourceUri = request.uri().toString();
                return resp(200, DATA_JSON, null);
            }
            return resp(404, "", null);
        }

        private static String bodyOf(HttpRequest request) {
            return request.bodyPublisher().map(bp -> {
                StringSubscriber s = new StringSubscriber();
                bp.subscribe(s);
                return s.body();
            }).orElse("");
        }

        @SuppressWarnings("unchecked")
        private static <T> HttpResponse<T> resp(int status, String body, String location) {
            HttpHeaders headers = HttpHeaders.of(
                    location == null ? Map.of() : Map.of("Location", List.of(location)),
                    (a, b) -> true);
            return new HttpResponse<>() {
                public int statusCode() {
                    return status;
                }

                public HttpRequest request() {
                    return null;
                }

                public Optional<HttpResponse<T>> previousResponse() {
                    return Optional.empty();
                }

                public HttpHeaders headers() {
                    return headers;
                }

                public T body() {
                    return (T) body;
                }

                public Optional<SSLSession> sslSession() {
                    return Optional.empty();
                }

                public URI uri() {
                    return null;
                }

                public HttpClient.Version version() {
                    return Version.HTTP_1_1;
                }
            };
        }

        // Unused abstract methods.
        @Override public Optional<CookieHandler> cookieHandler() { return Optional.empty(); }
        @Override public Optional<Duration> connectTimeout() { return Optional.empty(); }
        @Override public Redirect followRedirects() { return Redirect.NEVER; }
        @Override public Optional<ProxySelector> proxy() { return Optional.empty(); }
        @Override public SSLParameters sslParameters() { return new SSLParameters(); }
        @Override public Optional<Authenticator> authenticator() { return Optional.empty(); }
        @Override public Version version() { return Version.HTTP_1_1; }
        @Override public Optional<Executor> executor() { return Optional.empty(); }

        @Override
        public SSLContext sslContext() {
            try {
                return SSLContext.getDefault();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r, HttpResponse.BodyHandler<T> h) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest r, HttpResponse.BodyHandler<T> h,
                                                                HttpResponse.PushPromiseHandler<T> p) {
            throw new UnsupportedOperationException();
        }
    }

    /** Minimal Flow.Subscriber that concatenates a request body publisher into a String. */
    private static final class StringSubscriber implements java.util.concurrent.Flow.Subscriber<java.nio.ByteBuffer> {
        private final StringBuilder sb = new StringBuilder();

        public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
            subscription.request(Long.MAX_VALUE);
        }

        public void onNext(java.nio.ByteBuffer item) {
            byte[] bytes = new byte[item.remaining()];
            item.get(bytes);
            sb.append(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        }

        public void onError(Throwable throwable) {
        }

        public void onComplete() {
        }

        String body() {
            return sb.toString();
        }
    }
}
