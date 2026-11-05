package com.studyroombooker.backend.repository;

import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ShiftRepository {
    private final JdbcTemplate jdbc;

    public ShiftRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ShiftSummary(long id, Instant startsAt, Instant endsAt, String status,
                               String plannedRoomCode, String memberName) {}

    public List<ShiftSummary> findAll() {
        return jdbc.query("""
            SELECT s.id, s.starts_at, s.ends_at, s.status, r.code, m.name
            FROM shift s
            JOIN room r ON r.id = s.planned_room_id
            LEFT JOIN member m ON m.id = s.member_id
            ORDER BY s.starts_at, s.id
            """, (row, index) -> new ShiftSummary(row.getLong("id"),
                row.getObject("starts_at", OffsetDateTime.class).toInstant(),
                row.getObject("ends_at", OffsetDateTime.class).toInstant(),
                row.getString("status"), row.getString("code"), row.getString("name")));
    }
}
