package com.studyroombooker.backend;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlanningSchemaTests extends PostgresIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    long member;
    long room;
    long block;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE shift, study_block_backup_room, study_block, member_unavailability, member, room, library_hours RESTART IDENTITY CASCADE");
        member = jdbc.queryForObject("INSERT INTO member(name, email) VALUES ('Test Member', 'member@example.test') RETURNING id", Long.class);
        room = jdbc.queryForObject("INSERT INTO room(code, name) VALUES ('TEST', 'Test Room') RETURNING id", Long.class);
        block = jdbc.queryForObject("INSERT INTO study_block(kind, date, start_time, end_time, preferred_room_id) VALUES ('ONE_TIME', '2026-11-10', '08:00', '20:00', ?) RETURNING id", Long.class, room);
    }

    void shift(String start, String end, String status, Long assignee) {
        jdbc.update("INSERT INTO shift(study_block_id, planned_room_id, starts_at, ends_at, status, member_id) VALUES (?, ?, ?::timestamptz, ?::timestamptz, ?, ?)",
            block, room, start, end, status, assignee);
    }

    @Test
    void migrationAppliedAndLocalDateUsesChicagoRegardlessOfSessionTimezone() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM flyway_schema_history WHERE version = '1' AND success", Integer.class)).isEqualTo(1);
        jdbc.execute("SET TIME ZONE 'UTC'");
        try {
            shift("2026-11-11T01:00Z", "2026-11-11T02:00Z", "ASSIGNED", member);
            assertThat(jdbc.queryForObject("SELECT local_date::text FROM shift", String.class)).isEqualTo("2026-11-10");
        } finally {
            jdbc.execute("SET TIME ZONE 'America/Chicago'");
        }
    }

    @Test
    void rejectsInvalidDurationAlignmentAndDuplicateGeneration() {
        assertThatThrownBy(() -> shift("2026-11-10T14:00Z", "2026-11-10T18:15Z", "UNASSIGNED", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> shift("2026-11-10T14:01Z", "2026-11-10T15:00Z", "UNASSIGNED", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> shift("2026-11-10T14:00Z", "2026-11-10T14:00Z", "UNASSIGNED", null)).isInstanceOf(DataIntegrityViolationException.class);
        shift("2026-11-10T14:00Z", "2026-11-10T18:00Z", "UNASSIGNED", null);
        assertThatThrownBy(() -> shift("2026-11-10T14:00Z", "2026-11-10T18:00Z", "UNASSIGNED", null)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void preventsSecondDailyAssignmentAndAllowsReassignmentAfterCancellation() {
        shift("2026-11-10T14:00Z", "2026-11-10T15:00Z", "ASSIGNED", member);
        assertThatThrownBy(() -> shift("2026-11-10T18:00Z", "2026-11-10T19:00Z", "ASSIGNED", member)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("UPDATE shift SET status = 'CANCELLED'");
        shift("2026-11-10T18:00Z", "2026-11-10T19:00Z", "ASSIGNED", member);
    }

    @Test
    void preventsOverlapsAcrossLocalMidnight() {
        shift("2026-11-11T05:00Z", "2026-11-11T07:00Z", "ASSIGNED", member);
        assertThatThrownBy(() -> shift("2026-11-11T06:00Z", "2026-11-11T08:00Z", "ASSIGNED", member)).isInstanceOf(DataIntegrityViolationException.class);
        shift("2026-11-11T07:00Z", "2026-11-11T08:00Z", "ASSIGNED", member);
    }

    @Test
    void validatesScheduleShapesAndBackupOrdering() {
        assertThatThrownBy(() -> jdbc.update("INSERT INTO study_block(kind, start_time, end_time, preferred_room_id) VALUES ('ONE_TIME', '08:00', '09:00', ?)", room)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO member_unavailability(member_id, kind, weekday, valid_from, start_time, end_time) VALUES (?, 'WEEKLY', 8, '2026-11-10', '08:00', '09:00')", member)).isInstanceOf(DataIntegrityViolationException.class);
        jdbc.update("INSERT INTO member_unavailability(member_id, kind, weekday, valid_from, start_time, end_time) VALUES (?, 'WEEKLY', 2, '2026-11-10', '08:00', '09:00')", member);
        jdbc.update("INSERT INTO library_hours VALUES (2, '08:00', '24:00')");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO library_hours VALUES (3, '18:00', '08:00')")).isInstanceOf(DataIntegrityViolationException.class);
        long backup = jdbc.queryForObject("INSERT INTO room(code, name) VALUES ('BACKUP', 'Backup') RETURNING id", Long.class);
        jdbc.update("INSERT INTO study_block_backup_room VALUES (?, ?, 1)", block, backup);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO study_block_backup_room VALUES (?, ?, 1)", block, room)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO study_block_backup_room VALUES (?, ?, 2)", block, backup)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void enforcesMembershipBookingAndReferenceIntegrity() {
        assertThatThrownBy(() -> shift("2026-11-10T14:00Z", "2026-11-10T15:00Z", "ASSIGNED", null)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> shift("2026-11-10T14:00Z", "2026-11-10T15:00Z", "BOOKED", member)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> shift("2026-11-10T14:00Z", "2026-11-10T15:00Z", "ASSIGNED", 999L)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("INSERT INTO member(name, email) VALUES ('Duplicate', 'MEMBER@example.test')")).isInstanceOf(DataIntegrityViolationException.class);
    }
}
