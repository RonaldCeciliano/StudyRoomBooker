package com.studyroombooker.backend;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import com.studyroombooker.backend.email.EmailMessage;
import com.studyroombooker.backend.email.EmailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "app.base-url=https://app.example.test/")
@Import(MagicLinkAuthTests.Fakes.class)
class MagicLinkAuthTests extends PostgresIntegrationTest {
    private static final Pattern LINK = Pattern.compile("https://app\\.example\\.test/login/magic\\?token=([A-Za-z0-9_-]+)");

    /** Mutable clock and an outbox that records magic-link emails instead of logging them. */
    @TestConfiguration
    static class Fakes {
        static final MutableClock CLOCK = new MutableClock();
        static final BlockingQueue<EmailMessage> OUTBOX = new LinkedBlockingQueue<>();

        @Bean @Primary Clock testClock() { return CLOCK; }
        @Bean @Primary EmailSender capturingSender() { return OUTBOX::add; }
    }

    static final class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2026-11-05T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("America/Chicago"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Autowired JdbcTemplate jdbc;
    @Value("${local.server.port}") int port;
    TestHttp http;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE shift, study_block_backup_room, study_block, member_unavailability, member, room, library_hours RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO member(name, email) VALUES ('Active Member', 'Member@Example.test')");
        jdbc.update("INSERT INTO member(name, email, active) VALUES ('Former Member', 'former@example.test', false)");
        Fakes.CLOCK.now = Instant.parse("2026-11-05T12:00:00Z");
        Fakes.OUTBOX.clear();
        http = new TestHttp(port);
    }

    @Test
    void magicLinkSignsInOnlyOnPostAndOnlyOnce() throws Exception {
        assertThat(http.postForm("/ott/generate", "username", "member@example.test").statusCode()).isEqualTo(204);
        String token = emailedToken("Member@Example.test");

        // Opening the link (GET) must never consume the token or start a session.
        assertThat(http.get("/login/ott?token=" + token).statusCode()).isNotEqualTo(204);
        assertThat(http.get("/api/shifts").statusCode()).isEqualTo(401);
        assertThat(tokenRows()).isEqualTo(1);

        var login = http.postForm("/login/ott", "token", token);
        assertThat(login.statusCode()).isEqualTo(204);
        assertThat(tokenRows()).isZero();
        String sessionCookie = login.headers().allValues("Set-Cookie").stream()
            .filter(value -> value.startsWith("JSESSIONID=")).findFirst().orElseThrow();
        assertThat(sessionCookie).contains("HttpOnly").containsIgnoringCase("SameSite=Lax");

        var session = JsonMapper.builder().build().readTree(http.get("/api/session").body());
        assertThat(session.get("authenticated").asBoolean()).isTrue();
        assertThat(session.get("email").asString()).isEqualTo("Member@Example.test");
        assertThat(session.get("name").asString()).isEqualTo("Active Member");
        assertThat(http.get("/api/shifts").statusCode()).isEqualTo(200);

        var reuse = new TestHttp(port).postForm("/login/ott", "token", token);
        assertProblem(reuse.statusCode(), reuse.headers().firstValue("Content-Type").orElse(""), 401);
    }

    @Test
    void unknownAndInactiveEmailsGetTheSameResponseAndNoEmail() throws Exception {
        for (String email : new String[] {"stranger@example.test", "former@example.test", ""}) {
            var response = http.postForm("/ott/generate", "username", email);
            assertThat(response.statusCode()).isEqualTo(204);
            assertThat(response.body()).isEmpty();
        }
        assertThat(Fakes.OUTBOX.poll(500, TimeUnit.MILLISECONDS)).isNull();
        assertThat(tokenRows()).isZero();
    }

    @Test
    void expiredTokensAreRejected() throws Exception {
        http.postForm("/ott/generate", "username", "member@example.test");
        String token = emailedToken("Member@Example.test");
        Fakes.CLOCK.now = Fakes.CLOCK.now.plus(Duration.ofMinutes(15));
        assertThat(http.postForm("/login/ott", "token", token).statusCode()).isEqualTo(401);
        assertThat(http.get("/api/shifts").statusCode()).isEqualTo(401);
    }

    @Test
    void tokensAreStoredOnlyAsHashes() throws Exception {
        http.postForm("/ott/generate", "username", "member@example.test");
        String token = emailedToken("Member@Example.test");
        byte[] stored = jdbc.queryForObject("SELECT token_hash FROM login_token", byte[].class);
        assertThat(stored).hasSize(32);
        assertThat(new String(stored, java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain(token);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM login_token WHERE encode(token_hash, 'escape') LIKE ?",
            Integer.class, "%" + token + "%")).isZero();
    }

    @Test
    void membersDeactivatedAfterRequestingCannotSignIn() throws Exception {
        http.postForm("/ott/generate", "username", "member@example.test");
        String token = emailedToken("Member@Example.test");
        jdbc.update("UPDATE member SET active = false");
        assertThat(http.postForm("/login/ott", "token", token).statusCode()).isEqualTo(401);
    }

    @Test
    void repeatedRequestsWithinTheCooldownSendOneEmail() throws Exception {
        http.postForm("/ott/generate", "username", "member@example.test");
        http.postForm("/ott/generate", "username", "MEMBER@example.test");
        emailedToken("Member@Example.test");
        assertThat(Fakes.OUTBOX.poll(500, TimeUnit.MILLISECONDS)).isNull();
        Fakes.CLOCK.now = Fakes.CLOCK.now.plus(Duration.ofSeconds(61));
        http.postForm("/ott/generate", "username", "member@example.test");
        emailedToken("Member@Example.test");
    }

    @Test
    void writesRequireTheCsrfTokenAndApiRequiresASession() throws Exception {
        var noCsrf = http.send(http.request("/ott/generate").header("Content-Type", "application/x-www-form-urlencoded")
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString("username=member%40example.test")));
        assertThat(noCsrf.statusCode()).isEqualTo(403);
        assertThat(Fakes.OUTBOX.poll(300, TimeUnit.MILLISECONDS)).isNull();

        var anonymous = http.get("/api/shifts");
        assertProblem(anonymous.statusCode(), anonymous.headers().firstValue("Content-Type").orElse(""), 401);
        var session = JsonMapper.builder().build().readTree(http.get("/api/session").body());
        assertThat(session.get("authenticated").asBoolean()).isFalse();
        assertThat(session.get("email").isNull()).isTrue();
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        http.postForm("/ott/generate", "username", "member@example.test");
        http.postForm("/login/ott", "token", emailedToken("Member@Example.test"));
        assertThat(http.get("/api/shifts").statusCode()).isEqualTo(200);

        assertThat(http.postForm("/logout", "unused", "").statusCode()).isEqualTo(204);
        assertThat(http.get("/api/shifts").statusCode()).isEqualTo(401);
    }

    private String emailedToken(String to) throws InterruptedException {
        EmailMessage message = Fakes.OUTBOX.poll(5, TimeUnit.SECONDS);
        assertThat(message).as("magic-link email").isNotNull();
        assertThat(message.to()).isEqualTo(to);
        var matcher = LINK.matcher(message.body());
        assertThat(matcher.find()).as("link in %s", message.body()).isTrue();
        assertThat(message.body()).contains("expires in 15 minutes");
        return matcher.group(1);
    }

    private int tokenRows() {
        return jdbc.queryForObject("SELECT count(*) FROM login_token", Integer.class);
    }

    private static void assertProblem(int status, String contentType, int expected) {
        assertThat(status).isEqualTo(expected);
        assertThat(contentType).contains("application/problem+json");
    }
}
