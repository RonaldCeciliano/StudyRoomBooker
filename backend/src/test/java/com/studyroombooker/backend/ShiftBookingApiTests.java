package com.studyroombooker.backend;

import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Locale;
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
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(ShiftBookingApiTests.FixedTime.class)
class ShiftBookingApiTests extends PostgresIntegrationTest {
    static final Instant NOW = Instant.parse("2026-11-05T12:00:00Z");

    @TestConfiguration
    static class FixedTime {
        @Bean @Primary Clock testClock() { return Clock.fixed(NOW, ZoneId.of("America/Chicago")); }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MagicLinkTokenService tokens;
    @Value("${local.server.port}") int port;
    final Map<String, TestHttp> sessions = new HashMap<>();
    long target;

    @BeforeEach
    void seed() {
        sessions.clear();
        jdbc.execute("TRUNCATE shift, study_block_backup_room, study_block, member_unavailability, member, room, library_hours RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO member(name, email) VALUES ('Owner', 'owner@example.test'), ('Other', 'other@example.test')");
        jdbc.update("INSERT INTO room(code, name) VALUES ('A', 'Planned'), ('B', 'Backup'), ('C', 'Unrelated'), ('D', 'Inactive backup')");
        jdbc.update("UPDATE room SET active = false WHERE code = 'D'");
        jdbc.update("INSERT INTO study_block(kind, date, start_time, end_time, preferred_room_id) VALUES ('ONE_TIME', '2026-11-10', '12:00', '15:00', 1)");
        jdbc.update("INSERT INTO study_block_backup_room VALUES (1, 2, 1), (1, 4, 2)");
        target = jdbc.queryForObject("INSERT INTO shift(study_block_id, planned_room_id, starts_at, ends_at, member_id, status) VALUES (1, 1, '2026-11-10T18:00Z', '2026-11-10T21:00Z', 1, 'ASSIGNED') RETURNING id", Long.class);
    }

    @Test
    void ownerMarksBookedInThePlannedRoomByDefault() throws Exception {
        var response = post(target, "owner@example.test", "");
        assertThat(response.statusCode()).isEqualTo(204);
        var row = jdbc.queryForMap("SELECT status, booked_room_id, booked_at, member_id FROM shift WHERE id = ?", target);
        assertThat(row.get("status")).isEqualTo("BOOKED");
        assertThat(row.get("booked_room_id")).isEqualTo(1L);
        assertThat(row.get("member_id")).isEqualTo(1L);
        assertThat(((java.sql.Timestamp) row.get("booked_at")).toInstant()).isEqualTo(NOW);

        var shift = JsonMapper.builder().build().readTree(session("owner@example.test").get("/api/shifts").body()).get(0);
        assertThat(shift.get("status").asString()).isEqualTo("BOOKED");
        assertThat(shift.get("bookedRoomCode").asString()).isEqualTo("A");
        assertThat(shift.get("assignedToMe").asBoolean()).isTrue();
    }

    @Test
    void ownerMayChooseABackupRoomAndLaterCorrectItWithoutChangingBookedAt() throws Exception {
        assertThat(post(target, "owner@example.test", "{\"bookedRoomId\":2}").statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT booked_room_id FROM shift WHERE id = ?", Long.class, target)).isEqualTo(2);
        jdbc.update("UPDATE shift SET booked_at = '2026-11-04T10:00Z' WHERE id = ?", target);

        assertThat(post(target, "owner@example.test", "{\"bookedRoomId\":1}").statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT booked_room_id FROM shift WHERE id = ?", Long.class, target)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT booked_at FROM shift WHERE id = ?", OffsetDateTime.class, target).toInstant())
            .isEqualTo(Instant.parse("2026-11-04T10:00:00Z"));
        // Repeating without a room keeps the current booked room.
        assertThat(post(target, "owner@example.test", "").statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT booked_room_id FROM shift WHERE id = ?", Long.class, target)).isEqualTo(1);
    }

    @Test
    void rejectsRoomsOutsideThePlannedAndActiveBackupRooms() throws Exception {
        var before = jdbc.queryForMap("SELECT * FROM shift WHERE id = ?", target);
        for (long room : new long[] {3, 4, 999}) {
            assertProblem(post(target, "owner@example.test", "{\"bookedRoomId\":" + room + "}"), 400);
        }
        assertThat(jdbc.queryForMap("SELECT * FROM shift WHERE id = ?", target)).isEqualTo(before);
    }

    @Test
    void onlyTheSignedInOwnerCanMarkBooked() throws Exception {
        var before = jdbc.queryForMap("SELECT * FROM shift WHERE id = ?", target);
        assertProblem(post(target, null, ""), 401);
        assertProblem(post(target, "other@example.test", "{\"memberId\":1}"), 403);
        assertProblem(post(999, "owner@example.test", ""), 404);
        assertThat(jdbc.queryForMap("SELECT * FROM shift WHERE id = ?", target)).isEqualTo(before);
    }

    @Test
    void rejectsUnassignedMissedCancelledAndEndedShifts() throws Exception {
        for (String status : new String[] {"MISSED", "CANCELLED"}) {
            jdbc.update("UPDATE shift SET status = ? WHERE id = ?", status, target);
            assertProblem(post(target, "owner@example.test", ""), 409);
        }
        jdbc.update("UPDATE shift SET status = 'UNASSIGNED', member_id = NULL WHERE id = ?", target);
        assertProblem(post(target, "owner@example.test", ""), 403);
        jdbc.update("UPDATE shift SET status = 'ASSIGNED', member_id = 1, starts_at = '2026-11-05T09:00Z', ends_at = '2026-11-05T12:00Z' WHERE id = ?", target);
        assertProblem(post(target, "owner@example.test", ""), 409);
        assertThat(jdbc.queryForObject("SELECT status FROM shift WHERE id = ?", String.class, target)).isEqualTo("ASSIGNED");

        // A shift already in progress can still be confirmed.
        jdbc.update("UPDATE shift SET starts_at = '2026-11-05T11:00Z', ends_at = '2026-11-05T13:00Z' WHERE id = ?", target);
        assertThat(post(target, "owner@example.test", "").statusCode()).isEqualTo(204);
    }

    @Test
    void bookedShiftsCountForAssignmentAndCannotBeReleased() throws Exception {
        assertThat(post(target, "owner@example.test", "").statusCode()).isEqualTo(204);
        assertProblem(session("owner@example.test").postJson("/api/shifts/" + target + "/release", ""), 409);
        assertThat(jdbc.queryForObject("SELECT member_id FROM shift WHERE id = ?", Long.class, target)).isEqualTo(1);
    }

    @Test
    void listingFlagsOnlyTheViewersShiftsAndListsBookingRooms() throws Exception {
        var mine = JsonMapper.builder().build().readTree(session("owner@example.test").get("/api/shifts").body()).get(0);
        assertThat(mine.get("assignedToMe").asBoolean()).isTrue();
        assertThat(mine.get("bookedRoomCode").isNull()).isTrue();
        var rooms = mine.get("bookingRooms");
        assertThat(rooms.size()).isEqualTo(2);
        assertThat(rooms.get(0).get("code").asString()).isEqualTo("A");
        assertThat(rooms.get(1).get("code").asString()).isEqualTo("B");
        assertThat(rooms.get(1).get("id").asLong()).isEqualTo(2);
        assertThat(rooms.get(1).get("name").asString()).isEqualTo("Backup");

        var theirs = JsonMapper.builder().build().readTree(session("other@example.test").get("/api/shifts").body()).get(0);
        assertThat(theirs.get("assignedToMe").asBoolean()).isFalse();
    }

    private HttpResponse<String> post(long id, String email, String body) throws Exception {
        var http = email == null ? new TestHttp(port) : session(email);
        return http.postJson("/api/shifts/" + id + "/booked", body);
    }

    private TestHttp session(String email) throws Exception {
        String key = email.toLowerCase(Locale.ROOT);
        if (!sessions.containsKey(key)) {
            sessions.put(key, TestHttp.signedIn(port, tokens, email));
        }
        return sessions.get(key);
    }

    private void assertProblem(HttpResponse<String> response, int status) {
        assertThat(response.statusCode()).as(response.body()).isEqualTo(status);
        assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
    }
}
