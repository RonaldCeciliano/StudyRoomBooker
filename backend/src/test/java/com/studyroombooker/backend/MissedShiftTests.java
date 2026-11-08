package com.studyroombooker.backend;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import com.studyroombooker.backend.service.MissedShiftService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(MissedShiftTests.FixedTime.class)
class MissedShiftTests extends PostgresIntegrationTest {
    // Thursday, November 5, 2026, 6:00 AM Chicago.
    @TestConfiguration
    static class FixedTime {
        @Bean @Primary Clock testClock() {
            return Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneId.of("America/Chicago"));
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MissedShiftService missed;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE notification, shift, study_block_backup_room, study_block, member_unavailability, member, room, library_hours RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO member(name, email) VALUES ('One', 'one@example.test'), ('Two', 'two@example.test')");
        jdbc.update("INSERT INTO room(code, name) VALUES ('A', 'Room A')");
        jdbc.update("INSERT INTO library_hours SELECT day, time '00:00', time '24:00' FROM generate_series(1, 7) AS day");
        jdbc.update("INSERT INTO study_block(kind, date, start_time, end_time, preferred_room_id) VALUES ('ONE_TIME', '2026-11-04', '00:00', '24:00', 1)");
    }

    long shift(String start, String end, Long member, String status) {
        return jdbc.queryForObject("""
            INSERT INTO shift(study_block_id, planned_room_id, starts_at, ends_at, member_id, status, booked_room_id, booked_at)
            VALUES (1, 1, ?::timestamptz, ?::timestamptz, ?, ?, CASE WHEN ? = 'BOOKED' THEN 1 END, CASE WHEN ? = 'BOOKED' THEN now() END)
            RETURNING id
            """, Long.class, start, end, member, status, status, status);
    }

    String status(long id) {
        return jdbc.queryForObject("SELECT status FROM shift WHERE id = ?", String.class, id);
    }

    @Test
    void marksOnlyEndedUnbookedAssignmentsMissed() {
        long ended = shift("2026-11-04T18:00Z", "2026-11-04T22:00Z", 1L, "ASSIGNED");
        long endsNow = shift("2026-11-05T09:00Z", "2026-11-05T12:00Z", 2L, "ASSIGNED");
        long inProgress = shift("2026-11-05T11:00Z", "2026-11-05T13:00Z", 1L, "ASSIGNED");
        long booked = shift("2026-11-04T14:00Z", "2026-11-04T16:00Z", 2L, "BOOKED");
        long unassigned = shift("2026-11-04T10:00Z", "2026-11-04T12:00Z", null, "UNASSIGNED");

        assertThat(missed.markOverdue()).isEqualTo(2);

        assertThat(status(ended)).isEqualTo("MISSED");
        assertThat(status(endsNow)).isEqualTo("MISSED");
        assertThat(status(inProgress)).isEqualTo("ASSIGNED");
        assertThat(status(booked)).isEqualTo("BOOKED");
        assertThat(status(unassigned)).isEqualTo("UNASSIGNED");
        assertThat(jdbc.queryForObject("SELECT member_id FROM shift WHERE id = ?", Long.class, ended)).isEqualTo(1);

        assertThat(missed.markOverdue()).isZero();
    }
}
