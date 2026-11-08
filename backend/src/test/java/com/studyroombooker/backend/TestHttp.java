package com.studyroombooker.backend;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import com.studyroombooker.backend.auth.MagicLinkTokenService;
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Cookie-aware HTTP client for integration tests. Writes send the SPA CSRF header, and
 * {@link #signIn} goes through the real {@code POST /login/ott} flow with a freshly issued token.
 */
final class TestHttp {
    private final int port;
    private final CookieManager cookies = new CookieManager();
    private final HttpClient client;

    TestHttp(int port) {
        this.port = port;
        this.client = HttpClient.newBuilder().cookieHandler(cookies).build();
    }

    static TestHttp signedIn(int port, MagicLinkTokenService tokens, String email) throws Exception {
        var http = new TestHttp(port);
        http.signIn(tokens, email);
        return http;
    }

    void signIn(MagicLinkTokenService tokens, String email) throws Exception {
        var issued = tokens.generate(new GenerateOneTimeTokenRequest(email));
        assertThat(issued.deliverable()).as("token issued for %s", email).isTrue();
        assertThat(postForm("/login/ott", "token", issued.getTokenValue()).statusCode()).isEqualTo(204);
    }

    HttpResponse<String> get(String path) throws Exception {
        return send(request(path).GET());
    }

    HttpResponse<String> postJson(String path, String body) throws Exception {
        return send(request(path).header("Content-Type", "application/json")
            .header("X-XSRF-TOKEN", csrf()).POST(HttpRequest.BodyPublishers.ofString(body)));
    }

    HttpResponse<String> postForm(String path, String field, String value) throws Exception {
        String form = field + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8);
        return send(request(path).header("Content-Type", "application/x-www-form-urlencoded")
            .header("X-XSRF-TOKEN", csrf()).POST(HttpRequest.BodyPublishers.ofString(form)));
    }

    HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
    }

    HttpResponse<String> send(HttpRequest.Builder request) throws Exception {
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    /** The XSRF-TOKEN cookie value, fetching it from {@code GET /api/session} when absent. */
    String csrf() throws Exception {
        String token = cookie("XSRF-TOKEN");
        if (token == null) {
            get("/api/session");
            token = cookie("XSRF-TOKEN");
        }
        assertThat(token).as("XSRF-TOKEN cookie").isNotNull();
        return token;
    }

    String cookie(String name) {
        return cookies.getCookieStore().getCookies().stream()
            .filter(cookie -> cookie.getName().equals(name)).map(HttpCookie::getValue).findFirst().orElse(null);
    }
}
