package com.studyroombooker.backend;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import com.studyroombooker.backend.email.EmailDeliveryException;
import com.studyroombooker.backend.email.EmailMessage;
import com.studyroombooker.backend.email.EmailSender;
import com.studyroombooker.backend.notification.NotificationService;
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
    properties = "app.base-url=https://app.example.test")
@Import(ActionLinkApiTests.Fakes.class)
class ActionLinkApiTests extends PostgresIntegrationTest {
    static final Pattern LINK = Pattern.compile("https://app\\.example\\.test/actions/booked\\?token=([A-Za-z0-9_-]+)");

    @TestConfiguration
    static class Fakes {
        static final MutableClock CLOCK = new MutableClock();
        static final List<EmailMessage> SENT = new CopyOnWriteArrayList<>();
        static volatile boolean failing;

        @Bean @Primary Clock testClock() { return CLOCK; }
        @Bean @Primary EmailSender fakeSender() {
            return message -> {
                if (failing) throw new EmailDeliveryException("SMTP down", null);
                SENT.add(message);
            };
        }
    }

    static final class MutableClock extends Clock {
        volatile Instant now = Instant.parse("2026-11-05T12:00:00Z");
        @Override public ZoneId getZone() { return ZoneId.of("America/Chicago"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationService notifications;
    @Value("${local.server.port}") int port;
    TestHttp http;
    long shift;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE action_token, notification, shift, study_block_backup_room, study_block, member_unavailability, member, room, library_hours RESTART IDENTITY CASCADE");
        Fakes.CLOCK.now = Instant.parse("2026-11-05T12:00:00Z");
        Fakes.SENT.clear();
        Fakes.failing = false;
        jdbc.update("INSERT INTO member(name, email) VALUES ('One', 'one@example.test')");
        jdbc.update("INSERT INTO room(code, name) VALUES ('441D', 'D')");
        jdbc.update("INSERT INTO study_block(kind, date, start_time, end_time, preferred_room_id) VALUES ('ONE_TIME', '2026-11-10', '18:00', '22:00', 1)");
        shift = jdbc.queryForObject("INSERT INTO shift(study_block_id, planned_room_id, starts_at, ends_at, member_id, status) VALUES (1, 1, '2026-11-11T00:00Z', '2026-11-11T04:00Z', 1, 'ASSIGNED') RETURNING id", Long.class);
        http = new TestHttp(port); // no session: the link must work from a mail app
    }

    String emailedToken() {
        notifications.runOnce();
        assertThat(Fakes.SENT).hasSize(1);
        var matcher = LINK.matcher(Fakes.SENT.getFirst().body());
        assertThat(matcher.find()).as(Fakes.SENT.getFirst().body()).isTrue();
        return matcher.group(1);
    }

    @Test
    void previewDoesNotConsumeAndConfirmMarksBookedOnce() throws Exception {
        String token = emailedToken();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM action_token", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM action_token WHERE encode(token_hash, 'escape') LIKE ?", Integer.class, "%" + token + "%")).isZero();

        for (int i = 0; i < 2; i++) {
            var preview = http.get("/api/actions/booked?token=" + token);
            assertThat(preview.statusCode()).isEqualTo(200);
            var json = JsonMapper.builder().build().readTree(preview.body());
            assertThat(json.get("plannedRoomCode").asString()).isEqualTo("441D");
            assertThat(json.get("memberName").asString()).isEqualTo("One");
            assertThat(json.get("status").asString()).isEqualTo("ASSIGNED");
        }
        assertThat(jdbc.queryForObject("SELECT status FROM shift WHERE id = ?", String.class, shift)).isEqualTo("ASSIGNED");

        assertThat(http.postJson("/api/actions/booked", "{\"token\":\"" + token + "\"}").statusCode()).isEqualTo(204);
        var row = jdbc.queryForMap("SELECT status, booked_room_id FROM shift WHERE id = ?", shift);
        assertThat(row.get("status")).isEqualTo("BOOKED");
        assertThat(row.get("booked_room_id")).isEqualTo(1L);

        assertThat(http.postJson("/api/actions/booked", "{\"token\":\"" + token + "\"}").statusCode()).isEqualTo(404);
        assertThat(http.get("/api/actions/booked?token=" + token).statusCode()).isEqualTo(404);
    }

    @Test
    void expiresWhenTheShiftEnds() throws Exception {
        String token = emailedToken();
        Fakes.CLOCK.now = Instant.parse("2026-11-11T04:00:00Z");
        assertThat(http.get("/api/actions/booked?token=" + token).statusCode()).isEqualTo(404);
        assertThat(http.postJson("/api/actions/booked", "{\"token\":\"" + token + "\"}").statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT status FROM shift WHERE id = ?", String.class, shift)).isEqualTo("ASSIGNED");
    }

    @Test
    void aRejectedActionKeepsTheLinkAndChangesNothing() throws Exception {
        String token = emailedToken();
        jdbc.update("UPDATE shift SET status = 'CANCELLED' WHERE id = ?", shift);
        var response = http.postJson("/api/actions/booked", "{\"token\":\"" + token + "\"}");
        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM action_token", Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT status FROM shift WHERE id = ?", String.class, shift)).isEqualTo("CANCELLED");
    }

    @Test
    void rejectsMissingAndForgedTokensAndRequiresCsrf() throws Exception {
        assertThat(http.get("/api/actions/booked").statusCode()).isEqualTo(404);
        assertThat(http.postJson("/api/actions/booked", "{\"token\":\"forged\"}").statusCode()).isEqualTo(404);
        assertThat(http.postJson("/api/actions/booked", "{}").statusCode()).isEqualTo(404);
        String token = emailedToken();
        var noCsrf = http.send(http.request("/api/actions/booked").header("Content-Type", "application/json")
            .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"token\":\"" + token + "\"}")));
        assertThat(noCsrf.statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT status FROM shift WHERE id = ?", String.class, shift)).isEqualTo("ASSIGNED");
    }

    @Test
    void aFailedSendLeavesNoUsableToken() {
        Fakes.failing = true;
        notifications.runOnce();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM action_token", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT status FROM notification", String.class)).isEqualTo("FAILED");
    }
}
