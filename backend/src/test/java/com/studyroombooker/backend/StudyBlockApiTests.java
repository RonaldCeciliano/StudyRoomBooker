package com.studyroombooker.backend;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import com.studyroombooker.backend.service.StudyBlockService;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(StudyBlockApiTests.FixedTime.class)
class StudyBlockApiTests extends PostgresIntegrationTest {
    @TestConfiguration
    static class FixedTime {
        @Bean @Primary Clock testClock() {
            return Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneId.of("America/Chicago"));
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired StudyBlockService blocks;
    @Value("${local.server.port}") int port;
    long room;
    long backup;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE shift, study_block_backup_room, study_block, member_unavailability, member, room, library_hours RESTART IDENTITY CASCADE");
        room = jdbc.queryForObject("INSERT INTO room(code, name) VALUES ('A', 'Test A') RETURNING id", Long.class);
        backup = jdbc.queryForObject("INSERT INTO room(code, name) VALUES ('B', 'Test B') RETURNING id", Long.class);
        jdbc.update("INSERT INTO library_hours SELECT day, time '00:00', time '24:00' FROM generate_series(1, 7) AS day");
    }

    @Test
    void createsBlockAndContiguousUnassignedShiftsAndRegeneratesWithoutDuplicates() throws Exception {
        var response = post(body("2026-11-10", "12:00", "21:00", room, "[" + backup + "]"));
        assertThat(response.statusCode()).isEqualTo(201);
        var json = JsonMapper.builder().build().readTree(response.body());
        long id = json.get("id").asLong();
        assertThat(json.get("kind").asString()).isEqualTo("ONE_TIME");
        assertThat(json.get("backupRoomIds").get(0).asLong()).isEqualTo(backup);
        assertThat(jdbc.queryForObject("SELECT priority FROM study_block_backup_room WHERE study_block_id = ?", Integer.class, id)).isEqualTo(1);
        assertThat(jdbc.queryForList("SELECT extract(epoch FROM ends_at - starts_at)::bigint FROM shift ORDER BY starts_at", Long.class)).containsExactly(10800L, 10800L, 10800L);
        assertThat(jdbc.queryForObject("SELECT min(starts_at) FROM shift", java.time.OffsetDateTime.class).toInstant()).isEqualTo(Instant.parse("2026-11-10T18:00:00Z"));
        var ids = jdbc.queryForList("SELECT id FROM shift ORDER BY starts_at", Long.class);
        long member = jdbc.queryForObject("INSERT INTO member(name, email) VALUES ('Test', 'test@example.test') RETURNING id", Long.class);
        jdbc.update("UPDATE shift SET status = 'ASSIGNED', member_id = ? WHERE id = ?", member, ids.getFirst());
        blocks.generateShifts(id);
        blocks.generateShifts(id);
        assertThat(jdbc.queryForList("SELECT id FROM shift ORDER BY starts_at", Long.class)).isEqualTo(ids);
        assertThat(jdbc.queryForObject("SELECT status FROM shift WHERE id = ?", String.class, ids.getFirst())).isEqualTo("ASSIGNED");
        try (var client = HttpClient.newHttpClient()) {
            var shifts = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/shifts")).GET().build(), HttpResponse.BodyHandlers.ofString());
            var data = JsonMapper.builder().build().readTree(shifts.body());
            assertThat(data.size()).isEqualTo(3);
            assertThat(data.get(1).get("status").asString()).isEqualTo("UNASSIGNED");
            assertThat(data.get(1).get("memberName").isNull()).isTrue();
        }
    }

    @Test
    void rejectsInvalidInputWithoutPartialWrites() throws Exception {
        String[] invalid = {
            "{}", "null", "{bad json}",
            body("2026-11-10", "12:01", "21:00", room, "[]"),
            body("2026-11-10", "21:00", "12:00", room, "[]"),
            body("2026-11-04", "12:00", "21:00", room, "[]"),
            body("2026-11-10", "12:00", "21:00", 999, "[]"),
            body("2026-11-10", "12:00", "21:00", room, "[999]"),
            body("2026-11-10", "12:00", "21:00", room, "[null]"),
            body("2026-11-10", "12:00", "21:00", room, "[" + room + "]"),
            body("2026-11-10", "12:00", "21:00", room, "[" + backup + "," + backup + "]"),
            body("2027-03-14", "02:00", "04:00", room, "[]"),
            body("2027-11-07", "01:00", "04:00", room, "[]")
        };
        for (var input : invalid) {
            var response = post(input);
            assertThat(response.statusCode()).as(input + " -> " + response.body()).isEqualTo(400);
            assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        }
        jdbc.update("UPDATE room SET active = false WHERE id = ?", backup);
        assertThat(post(body("2026-11-10", "12:00", "21:00", room, "[" + backup + "]")).statusCode()).isEqualTo(400);
        jdbc.update("UPDATE library_hours SET closes_at = '20:00'");
        assertThat(post(body("2026-11-10", "12:00", "21:00", room, "[]")).statusCode()).isEqualTo(400);
        jdbc.update("DELETE FROM library_hours");
        assertThat(post(body("2026-11-10", "12:00", "21:00", room, "[]")).statusCode()).isEqualTo(400);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM study_block", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shift", Integer.class)).isZero();
    }

    @Test
    void rollsBackBlockAndBackupsIfShiftInsertionFails() throws Exception {
        jdbc.execute("ALTER TABLE shift ADD CONSTRAINT force_write_failure CHECK (false)");
        try {
            assertThat(post(body("2026-11-10", "12:00", "21:00", room, "[" + backup + "]")).statusCode()).isEqualTo(500);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM study_block", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM study_block_backup_room", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM shift", Integer.class)).isZero();
        } finally {
            jdbc.execute("ALTER TABLE shift DROP CONSTRAINT force_write_failure");
        }
    }

    @Test
    void createImmediatelyAssignsEligibleMembersAndDashboardReturnsTheirNames() throws Exception {
        jdbc.update("INSERT INTO member(name, email) VALUES ('First', 'first@example.test'), ('Second', 'second@example.test')");
        var response = post(body("2026-11-10", "12:00", "21:00", room, "[]"));
        assertThat(response.statusCode()).isEqualTo(201);
        try (var client = HttpClient.newHttpClient()) {
            var shifts = client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/shifts"))
                .GET().build(), HttpResponse.BodyHandlers.ofString());
            var data = JsonMapper.builder().build().readTree(shifts.body());
            assertThat(data.size()).isEqualTo(3);
            assertThat(data.get(0).get("memberName").asString()).isEqualTo("First");
            assertThat(data.get(0).get("status").asString()).isEqualTo("ASSIGNED");
            assertThat(data.get(1).get("memberName").asString()).isEqualTo("Second");
            assertThat(data.get(1).get("status").asString()).isEqualTo("ASSIGNED");
            assertThat(data.get(2).get("memberName").isNull()).isTrue();
            assertThat(data.get(2).get("status").asString()).isEqualTo("UNASSIGNED");
        }
    }

    @Test
    void assignmentFailureRollsBackTheWholeCreate() throws Exception {
        jdbc.update("INSERT INTO member(name, email) VALUES ('First', 'first@example.test')");
        jdbc.execute("ALTER TABLE shift ADD CONSTRAINT force_assignment_failure CHECK (status <> 'ASSIGNED')");
        try {
            assertThat(post(body("2026-11-10", "12:00", "21:00", room, "[" + backup + "]")).statusCode()).isEqualTo(500);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM study_block", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM study_block_backup_room", Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM shift", Integer.class)).isZero();
        } finally {
            jdbc.execute("ALTER TABLE shift DROP CONSTRAINT force_assignment_failure");
        }
    }

    @Test
    void acceptsOmittedBackups() throws Exception {
        assertThat(post("{\"date\":\"2026-11-10\",\"startTime\":\"12:00\",\"endTime\":\"13:00\",\"preferredRoomId\":" + room + "}").statusCode()).isEqualTo(201);
    }

    private String body(String date, String start, String end, long preferred, String backups) {
        return """
            {"date":"%s","startTime":"%s","endTime":"%s","preferredRoomId":%d,"backupRoomIds":%s}
            """.formatted(date, start, end, preferred, backups);
    }

    private HttpResponse<String> post(String body) throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/study-blocks"))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
