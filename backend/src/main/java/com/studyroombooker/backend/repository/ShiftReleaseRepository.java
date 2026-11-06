package com.studyroombooker.backend.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ShiftReleaseRepository {
    private final JdbcTemplate jdbc;

    public ShiftReleaseRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record ReleaseShift(long id, Long memberId, String status, Instant startsAt) {}

    public Optional<Long> activeMember(String email) {
        return jdbc.query("SELECT id FROM member WHERE active AND lower(email) = lower(?) FOR SHARE",
            (row, index) -> row.getLong(1), email).stream().findFirst();
    }

    public Optional<ReleaseShift> lockShift(long id) {
        return jdbc.query("SELECT id, member_id, status, starts_at FROM shift WHERE id = ? FOR UPDATE",
            (row, index) -> new ReleaseShift(row.getLong("id"), row.getObject("member_id", Long.class),
                row.getString("status"), row.getObject("starts_at", OffsetDateTime.class).toInstant()), id)
            .stream().findFirst();
    }

    public void release(long shiftId) {
        jdbc.update("UPDATE shift SET status = 'UNASSIGNED', member_id = NULL WHERE id = ?", shiftId);
    }
}
