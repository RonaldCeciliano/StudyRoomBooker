package com.studyroombooker.backend.repository;

import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ShiftRepository {
    private final JdbcTemplate jdbc;

    public ShiftRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record RoomOption(long id, String code, String name) {}

    public record ShiftSummary(long id, Instant startsAt, Instant endsAt, String status,
                               String plannedRoomCode, String memberName, String bookedRoomCode,
                               boolean assignedToMe, List<RoomOption> bookingRooms,
                               boolean libcalCancelRequired) {}

    /** {@code viewerEmail} is the signed-in member, used only to flag their own shifts. */
    public List<ShiftSummary> findAll(String viewerEmail) {
        Map<Long, List<RoomOption>> rooms = bookingRooms();
        return jdbc.query("""
            SELECT s.id, s.starts_at, s.ends_at, s.status, r.code, m.name, br.code AS booked_code,
                   coalesce(lower(m.email) = lower(?), false) AS mine, s.libcal_cancel_required
            FROM shift s
            JOIN room r ON r.id = s.planned_room_id
            LEFT JOIN room br ON br.id = s.booked_room_id
            LEFT JOIN member m ON m.id = s.member_id
            ORDER BY s.starts_at, s.id
            """, (row, index) -> new ShiftSummary(row.getLong("id"),
                row.getObject("starts_at", OffsetDateTime.class).toInstant(),
                row.getObject("ends_at", OffsetDateTime.class).toInstant(),
                row.getString("status"), row.getString("code"), row.getString("name"),
                row.getString("booked_code"), row.getBoolean("mine"),
                rooms.getOrDefault(row.getLong("id"), List.of()),
                row.getBoolean("libcal_cancel_required")), viewerEmail);
    }

    /** Planned room first, then active backups by priority, for every shift. */
    private Map<Long, List<RoomOption>> bookingRooms() {
        record Row(long shiftId, RoomOption room) {}
        return jdbc.query("""
            SELECT shift_id, room_id, code, name FROM (
                SELECT s.id AS shift_id, r.id AS room_id, r.code, r.name, 0 AS priority
                FROM shift s JOIN room r ON r.id = s.planned_room_id
                UNION ALL
                SELECT s.id, r.id, r.code, r.name, b.priority
                FROM shift s
                JOIN study_block_backup_room b ON b.study_block_id = s.study_block_id
                JOIN room r ON r.id = b.room_id AND r.active
            ) rooms ORDER BY shift_id, priority
            """, (row, index) -> new Row(row.getLong("shift_id"),
                new RoomOption(row.getLong("room_id"), row.getString("code"), row.getString("name"))))
            .stream().collect(Collectors.groupingBy(Row::shiftId, java.util.LinkedHashMap::new,
                Collectors.mapping(Row::room, Collectors.toList())));
    }
}
