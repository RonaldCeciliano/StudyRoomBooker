package com.studyroombooker.backend;

import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
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
@Import(StudyBlockEditApiTests.FixedTime.class)
class StudyBlockEditApiTests extends PostgresIntegrationTest {
    @TestConfiguration
    static class FixedTime {
        @Bean @Primary Clock testClock() {
            return Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneId.of("America/Chicago"));
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MagicLinkTokenService tokens;
    @Value("${local.server.port}") int port;
    TestHttp http;
    long block;

    @BeforeEach
    void seed() throws Exception {
        jdbc.execute("TRUNCATE shift, study_block_backup_room, study_block, member_unavailability, member, room, library_hours RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO room(code, name) VALUES ('A', 'Room A'), ('B', 'Room B'), ('C', 'Room C')");
        jdbc.update("INSERT INTO room(code, name, active) VALUES ('Z', 'Closed room', false)");
        jdbc.update("INSERT INTO library_hours SELECT day, time '00:00', time '24:00' FROM generate_series(1, 7) AS day");
        jdbc.update("INSERT INTO member(name, email) VALUES ('One', 'one@example.test'), ('Two', 'two@example.test')");
        http = TestHttp.signedIn(port, tokens, "one@example.test");
        var created = http.postJson("/api/study-blocks", body("2026-11-10", "12:00", "18:00", 1, "[2]"));
        assertThat(created.statusCode()).isEqualTo(201);
        block = json(created).get("id").asLong();
    }

    @Test
    void listsUpcomingBlocksAndActiveRooms() throws Exception {
        jdbc.update("INSERT INTO study_block(kind, date, start_time, end_time, preferred_room_id) VALUES ('ONE_TIME', '2026-11-01', '12:00', '13:00', 1)");
        var blocks = json(http.get("/api/study-blocks"));
        assertThat(blocks.size()).isEqualTo(1);
        assertThat(blocks.get(0).get("date").asString()).isEqualTo("2026-11-10");
        assertThat(blocks.get(0).get("startTime").asString()).isEqualTo("12:00:00");
        assertThat(blocks.get(0).get("backupRoomIds").get(0).asLong()).isEqualTo(2);

        var rooms = json(http.get("/api/rooms"));
        assertThat(rooms.size()).isEqualTo(3);
        assertThat(rooms.get(0).get("code").asString()).isEqualTo("A");
        assertThat(rooms.get(0).get("name").asString()).isEqualTo("Room A");
        assertThat(new TestHttp(port).get("/api/rooms").statusCode()).isEqualTo(401);
    }

    @Test
    void changingTimesCancelsBookedShiftsForLibcalAndRegeneratesTheRest() throws Exception {
        var before = shifts();
        assertThat(before).hasSize(2);
        long bookedId = (Long) before.get(0).get("id");
        jdbc.update("UPDATE shift SET status = 'BOOKED', booked_room_id = 1, booked_at = now() WHERE id = ?", bookedId);

        var response = http.send(http.request("/api/study-blocks/" + block)
            .header("Content-Type", "application/json").header("X-XSRF-TOKEN", http.csrf())
            .PUT(java.net.http.HttpRequest.BodyPublishers.ofString(body("2026-11-10", "12:00", "15:00", 3, "[]"))));
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
        assertThat(json(response).get("preferredRoomId").asLong()).isEqualTo(3);

        var cancelled = jdbc.queryForMap("SELECT status, libcal_cancel_required, booked_room_id FROM shift WHERE id = ?", bookedId);
        assertThat(cancelled.get("status")).isEqualTo("CANCELLED");
        assertThat(cancelled.get("libcal_cancel_required")).isEqualTo(true);
        assertThat(cancelled.get("booked_room_id")).isEqualTo(1L);

        var active = jdbc.queryForList("SELECT starts_at, planned_room_id, status, member_id FROM shift WHERE study_block_id = ? AND status <> 'CANCELLED' ORDER BY starts_at", block);
        assertThat(active).hasSize(1); // 3 hours, same 12:00 start as the cancelled booked shift
        assertThat(active.get(0).get("planned_room_id")).isEqualTo(3L);
        assertThat(active.get(0).get("status")).isEqualTo("ASSIGNED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM study_block_backup_room", Integer.class)).isZero();

        var listed = json(http.get("/api/shifts"));
        boolean flagged = false;
        for (var shift : listed) {
            if (shift.get("id").asLong() == bookedId) flagged = shift.get("libcalCancelRequired").asBoolean();
        }
        assertThat(flagged).isTrue();
    }

    @Test
    void changingOnlyRoomsKeepsShiftsAssignmentsAndBookings() throws Exception {
        var before = shifts();
        long bookedId = (Long) before.get(0).get("id");
        jdbc.update("UPDATE shift SET status = 'BOOKED', booked_room_id = 1, booked_at = now() WHERE id = ?", bookedId);

        assertThat(put(block, body("2026-11-10", "12:00", "18:00", 3, "[1, 2]")).statusCode()).isEqualTo(200);

        var after = shifts();
        assertThat(after).extracting(row -> row.get("id")).isEqualTo(before.stream().map(row -> row.get("id")).toList());
        assertThat(after).extracting(row -> row.get("member_id")).isEqualTo(before.stream().map(row -> row.get("member_id")).toList());
        assertThat(after.get(0).get("status")).isEqualTo("BOOKED");
        assertThat(after.get(0).get("planned_room_id")).isEqualTo(1L);
        assertThat(after.get(1).get("planned_room_id")).isEqualTo(3L);
        assertThat(jdbc.queryForList("SELECT room_id FROM study_block_backup_room ORDER BY priority", Long.class)).containsExactly(1L, 2L);
    }

    @Test
    void rejectsInvalidEditsStartedBlocksAndMissingBlocksWithoutChanges() throws Exception {
        var before = shifts();
        assertThat(put(block, body("2026-11-10", "15:00", "12:00", 1, "[]")).statusCode()).isEqualTo(400);
        assertThat(put(block, body("2026-11-10", "12:00", "15:00", 4, "[]")).statusCode()).isEqualTo(400);
        assertThat(put(block, body("2026-11-04", "12:00", "15:00", 1, "[]")).statusCode()).isEqualTo(400);
        assertThat(put(999, body("2026-11-10", "12:00", "15:00", 1, "[]")).statusCode()).isEqualTo(404);
        assertThat(shifts()).isEqualTo(before);

        jdbc.update("UPDATE study_block SET date = '2026-11-05', start_time = '05:00', end_time = '08:00' WHERE id = ?", block);
        var started = put(block, body("2026-11-10", "12:00", "15:00", 1, "[]"));
        assertThat(started.statusCode()).isEqualTo(409);
        assertThat(started.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
    }

    private HttpResponse<String> put(long id, String body) throws Exception {
        return http.send(http.request("/api/study-blocks/" + id)
            .header("Content-Type", "application/json").header("X-XSRF-TOKEN", http.csrf())
            .PUT(java.net.http.HttpRequest.BodyPublishers.ofString(body)));
    }

    private List<Map<String, Object>> shifts() {
        return jdbc.queryForList("SELECT id, starts_at, planned_room_id, status, member_id FROM shift WHERE study_block_id = ? ORDER BY starts_at", block);
    }

    private static tools.jackson.databind.JsonNode json(HttpResponse<String> response) {
        return JsonMapper.builder().build().readTree(response.body());
    }

    private static String body(String date, String start, String end, long preferred, String backups) {
        return """
            {"date":"%s","startTime":"%s","endTime":"%s","preferredRoomId":%d,"backupRoomIds":%s}
            """.formatted(date, start, end, preferred, backups);
    }

    @Test
    void getsOneBlockAndOneShift() throws Exception {
        var one = json(http.get("/api/study-blocks/" + block));
        assertThat(one.get("id").asLong()).isEqualTo(block);
        assertThat(one.get("endTime").asString()).isEqualTo("18:00:00");
        assertThat(http.get("/api/study-blocks/999").statusCode()).isEqualTo(404);

        long shiftId = (Long) shifts().get(0).get("id");
        var shift = json(http.get("/api/shifts/" + shiftId));
        assertThat(shift.get("id").asLong()).isEqualTo(shiftId);
        assertThat(shift.get("plannedRoomCode").asString()).isEqualTo("A");
        assertThat(shift.has("assignedToMe")).isTrue();
        assertThat(http.get("/api/shifts/999").statusCode()).isEqualTo(404);
        assertThat(new TestHttp(port).get("/api/shifts/" + shiftId).statusCode()).isEqualTo(401);
    }

    @Test
    void listsActiveMemberNamesWithoutEmails() throws Exception {
        jdbc.update("INSERT INTO member(name, email, active) VALUES ('Gone', 'gone@example.test', false)");
        var members = json(http.get("/api/members"));
        assertThat(members.size()).isEqualTo(2);
        assertThat(members.get(0).get("name").asString()).isEqualTo("One");
        assertThat(members.get(0).has("email")).isFalse();
    }

    @Test
    void deletingAnUnbookedBlockRemovesItAndItsShifts() throws Exception {
        assertThat(delete(block).statusCode()).isEqualTo(204);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM study_block", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shift", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM study_block_backup_room", Integer.class)).isZero();
        assertThat(delete(block).statusCode()).isEqualTo(404);
    }

    @Test
    void deletingABlockWithABookingCancelsItForLibcalAndHidesTheBlock() throws Exception {
        long bookedId = (Long) shifts().get(0).get("id");
        jdbc.update("UPDATE shift SET status = 'BOOKED', booked_room_id = 1, booked_at = now() WHERE id = ?", bookedId);

        assertThat(delete(block).statusCode()).isEqualTo(204);
        var remaining = shifts();
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).get("status")).isEqualTo("CANCELLED");
        assertThat(jdbc.queryForObject("SELECT libcal_cancel_required FROM shift WHERE id = ?", Boolean.class, bookedId)).isTrue();
        assertThat(jdbc.queryForObject("SELECT cancelled_at IS NOT NULL FROM study_block WHERE id = ?", Boolean.class, block)).isTrue();
        assertThat(json(http.get("/api/study-blocks")).size()).isZero();
        assertThat(http.get("/api/study-blocks/" + block).statusCode()).isEqualTo(404);
        assertThat(put(block, body("2026-11-10", "12:00", "15:00", 1, "[]")).statusCode()).isEqualTo(404);
    }

    @Test
    void startedBlocksCannotBeDeleted() throws Exception {
        jdbc.update("UPDATE study_block SET date = '2026-11-05', start_time = '05:00', end_time = '08:00' WHERE id = ?", block);
        assertThat(delete(block).statusCode()).isEqualTo(409);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM study_block", Integer.class)).isEqualTo(1);
    }

    private HttpResponse<String> delete(long id) throws Exception {
        return http.send(http.request("/api/study-blocks/" + id).header("X-XSRF-TOKEN", http.csrf()).DELETE());
    }
}
