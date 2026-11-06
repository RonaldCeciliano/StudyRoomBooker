package com.studyroombooker.backend;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ShiftReleaseApiTests.TestIdentity.class)
class ShiftReleaseApiTests extends PostgresIntegrationTest {
    /** Test-classpath only: production has no header-to-principal adapter. */
    @TestConfiguration
    static class TestIdentity {
        @Bean @Primary Clock testClock() {
            return Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneId.of("America/Chicago"));
        }

        @Bean FilterRegistrationBean<Filter> testPrincipal() {
            Filter filter = (request, response, chain) -> {
                var http = (HttpServletRequest) request;
                String email = http.getHeader("X-Test-Email");
                chain.doFilter(new HttpServletRequestWrapper(http) {
                    @Override public Principal getUserPrincipal() {
                        return email == null ? null : () -> email;
                    }
                }, response);
            };
            return new FilterRegistrationBean<>(filter);
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Value("${local.server.port}") int port;
    long target;

    @BeforeEach
    void seed() {
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
        try (var client = HttpClient.newHttpClient()) {
            var shifts = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/shifts"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
            var json = JsonMapper.builder().build().readTree(shifts.body());
            assertThat(json.get(0).get("status").asString()).isEqualTo("ASSIGNED");
            assertThat(json.get(0).get("memberName").asString()).isEqualTo("Replacement");
        }
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
        assertProblem(post(target, "unknown@example.test", ""), 403);
        assertProblem(post(target, "replacement@example.test", "{\"memberId\":1,\"email\":\"owner@example.test\"}"), 403);
        jdbc.update("UPDATE member SET active = false WHERE id = 1");
        assertProblem(post(target, "owner@example.test", ""), 403);
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
        try (var client = HttpClient.newHttpClient()) {
            var response = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/shifts/" + target + "/release"))
                .header("X-Test-Email", "owner@example.test").GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).isEqualTo(405);
        }
        assertThat(jdbc.queryForMap("SELECT * FROM shift WHERE id = ?", target)).isEqualTo(before);
    }

    private HttpResponse<String> post(long id, String email, String body) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/shifts/" + id + "/release"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body));
            if (email != null) builder.header("X-Test-Email", email);
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private void assertProblem(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        assertThat(JsonMapper.builder().build().readTree(response.body()).get("status").asInt()).isEqualTo(status);
    }
}
