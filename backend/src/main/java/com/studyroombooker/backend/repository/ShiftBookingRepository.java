package com.studyroombooker.backend.repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ShiftBookingRepository {
    private final JdbcTemplate jdbc;

    public ShiftBookingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record BookingShift(long id, Long memberId, String status, Instant endsAt, Long bookedRoomId) {}

    public Optional<Long> activeMember(String email) {
        return jdbc.query("SELECT id FROM member WHERE active AND lower(email) = lower(?) FOR SHARE",
            (row, index) -> row.getLong(1), email).stream().findFirst();
    }

    public Optional<BookingShift> lockShift(long id) {
        return jdbc.query("SELECT id, member_id, status, ends_at, booked_room_id FROM shift WHERE id = ? FOR UPDATE",
            (row, index) -> new BookingShift(row.getLong("id"), row.getObject("member_id", Long.class),
                row.getString("status"), row.getObject("ends_at", OffsetDateTime.class).toInstant(),
                row.getObject("booked_room_id", Long.class)), id)
            .stream().findFirst();
    }

    /** The planned room first, then the block's active backup rooms in priority order. */
    public List<Long> bookableRoomIds(long shiftId) {
        return jdbc.queryForList("""
            SELECT room_id FROM (
                SELECT s.planned_room_id AS room_id, 0 AS priority FROM shift s WHERE s.id = ?
                UNION ALL
                SELECT b.room_id, b.priority FROM shift s
                JOIN study_block_backup_room b ON b.study_block_id = s.study_block_id
                JOIN room r ON r.id = b.room_id AND r.active
                WHERE s.id = ?
            ) rooms ORDER BY priority
            """, Long.class, shiftId, shiftId);
    }

    /** Keeps the first booked_at so correcting the room does not rewrite when it was booked. */
    public void markBooked(long shiftId, long roomId, Instant now) {
        jdbc.update("""
            UPDATE shift SET status = 'BOOKED', booked_room_id = ?,
                booked_at = coalesce(booked_at, ?), updated_at = ?
            WHERE id = ?
            """, roomId, Timestamp.from(now), Timestamp.from(now), shiftId);
    }
}
