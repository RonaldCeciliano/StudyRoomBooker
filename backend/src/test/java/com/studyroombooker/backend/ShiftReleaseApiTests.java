package com.studyroombooker.backend;

import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import com.studyroombooker.backend.auth.MagicLinkTokenService;

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
import org.springframework.security.authentication.ott.GenerateOneTimeTokenRequest;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ShiftReleaseApiTests.FixedTime.class)
class ShiftReleaseApiTests extends PostgresIntegrationTest {
    @TestConfiguration
    static class FixedTime {
        @Bean @Primary Clock testClock() {
            return Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneId.of("America/Chicago"));
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MagicLinkTokenService tokens;
    @Value("${local.server.port}") int port;
    long target;
    /** Real magic-link sessions, one per member email for the current test. */
    final Map<String, TestHttp> sessions = new HashMap<>();

    @BeforeEach
    void seed() {
        sessions.clear();
        jdbc.execute("TRUNCATE shift, study_block_backup_room, study_block, member_unavailability, member, room, library_hours RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO member(name, email) VALUES ('Owner', 'owner@example.test'), ('Replacement', 'replacement@example.test')");
        jdbc.update("INSERT INTO room(code, name) VALUES ('A', 'Test Room')");
        jdbc.update("INSERT INTO study_block(kind, date, start_time, end_time, preferred_room_id) VALUES ('ONE_TIME', '2026-11-10', '12:00', '15:00', 1)");
        target = jdbc.queryForObject("INSERT INTO shift(study_block_id, planned_room_id, starts_at, ends_at, member_id, status) VALUES (1, 1, '2026-11-10T18:00Z', '2026-11-10T21:00Z', 1, 'ASSIGNED') RETURNING id", Long.class);
    }

    @Test
    void ownerReleaseReturnsNoContentAndDashboardShowsReplacement() throws Exception {
        var response = post(target, "OWNER@example.test", "");
        assertThat(response.statusCode()).isEqualTo(204);
        assertThat(response.body()).isEmpty();
        var json = JsonMapper.builder().build().readTree(session("owner@example.test").get("/api/shifts").body());
        assertThat(json.get(0).get("status").asString()).isEqualTo("ASSIGNED");
        assertThat(json.get(0).get("memberName").asString()).isEqualTo("Replacement");
        assertProblem(post(target, "owner@example.test", ""), 403);
        assertThat(jdbc.queryForObject("SELECT member_id FROM shift WHERE id = ?", Long.class, target)).isEqualTo(2);
    }

    @Test
    void noEligibleReplacementReturnsSuccessWithUnassignedShift() throws Exception {
        jdbc.update("UPDATE member SET active = false WHERE id = 2");
        assertThat(post(target, "owner@example.test", "").statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT member_id FROM shift WHERE id = ?", Long.class, target)).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM shift WHERE id = ?", String.class, target)).isEqualTo("UNASSIGNED");
    }

    @Test
    void rejectsMissingInactiveUnknownAndWrongOwnersWithoutMutating() throws Exception {
        var before = jdbc.queryForMap("SELECT * FROM shift WHERE id = ?", target);
        assertProblem(post(target, null, "{\"memberId\":1}"), 401);
        // Unknown emails cannot obtain a session at all.
        assertThat(tokens.generate(new GenerateOneTimeTokenRequest("unknown@example.test")).deliverable()).isFalse();
        assertProblem(post(target, "replacement@example.test", "{\"memberId\":1,\"email\":\"owner@example.test\"}"), 403);
        session("owner@example.test");
        jdbc.update("UPDATE member SET active = false WHERE id = 1");
        // Deactivation ends the existing session on its next request.
        assertProblem(post(target, "owner@example.test", ""), 401);
        assertThat(jdbc.queryForMap("SELECT * FROM shift WHERE id = ?", target)).isEqualTo(before);
    }

    @Test
    void rejectsMissingShiftWithNotFound() throws Exception {
        assertProblem(post(999, "owner@example.test", ""), 404);
    }

    @Test
    void rejectsBookedCancelledMissedAndStartedShiftsWithoutChangingBookingData() throws Exception {
        for (String status : new String[]{"BOOKED", "CANCELLED", "MISSED"}) {
            jdbc.update("UPDATE shift SET status = ?, booked_room_id = 1, booked_at = now(), libcal_cancel_required = true WHERE id = ?", status, target);
            var before = jdbc.queryForMap("SELECT * FROM shift WHERE id = ?", target);
            assertProblem(post(target, "owner@example.test", ""), 409);
            assertThat(jdbc.queryForMap("SELECT * FROM shift WHERE id = ?", target)).isEqualTo(before);
        }
        jdbc.update("UPDATE shift SET status = 'ASSIGNED', starts_at = '2026-11-05T12:00Z', ends_at = '2026-11-05T13:00Z' WHERE id = ?", target);
        var before = jdbc.queryForMap("SELECT * FROM shift WHERE id = ?", target);
        assertProblem(post(target, "owner@example.test", ""), 409);
        assertThat(jdbc.queryForMap("SELECT * FROM shift WHERE id = ?", target)).isEqualTo(before);
        jdbc.update("UPDATE shift SET starts_at = '2026-11-04T12:00Z', ends_at = '2026-11-04T13:00Z' WHERE id = ?", target);
        assertProblem(post(target, "owner@example.test", ""), 409);
    }

    @Test
    void getDoesNotRelease() throws Exception {
        var before = jdbc.queryForMap("SELECT * FROM shift WHERE id = ?", target);
        assertThat(session("owner@example.test").get("/api/shifts/" + target + "/release").statusCode()).isEqualTo(405);
        assertThat(jdbc.queryForMap("SELECT * FROM shift WHERE id = ?", target)).isEqualTo(before);
    }

    /** Posts as the signed-in member, or anonymously (with a valid CSRF token) when email is null. */
    private HttpResponse<String> post(long id, String email, String body) throws Exception {
        var http = email == null ? new TestHttp(port) : session(email);
        return http.postJson("/api/shifts/" + id + "/release", body);
    }

    private TestHttp session(String email) throws Exception {
        String key = email.toLowerCase(java.util.Locale.ROOT);
        if (!sessions.containsKey(key)) {
            sessions.put(key, TestHttp.signedIn(port, tokens, email));
        }
        return sessions.get(key);
    }

    private void assertProblem(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        assertThat(JsonMapper.builder().build().readTree(response.body()).get("status").asInt()).isEqualTo(status);
    }
}
