package com.studyroombooker.backend.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AssignmentRepository {
    private final JdbcTemplate jdbc;

    public AssignmentRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record PendingShift(long id, Instant startsAt, Instant endsAt, LocalDate localDate) {}
    public record Candidate(long id, long weeklyShifts, long weeklySeconds) {}
    // PostgreSQL time supports 24:00, which LocalTime cannot represent.
    public record Unavailability(String kind, LocalDate date, Integer weekday,
                                 LocalDate validFrom, LocalDate validUntil,
                                 int startSeconds, int endSeconds) {}

    /** Single-group transaction lock shared by generation and all assignment entry points. */
    public void lockAssignments() {
        jdbc.query("SELECT pg_advisory_xact_lock(730215011)", row -> {});
    }

    public List<PendingShift> pendingShifts(long blockId, Instant now) {
        return jdbc.query("""
            SELECT id, starts_at, ends_at, local_date FROM shift
            WHERE study_block_id = ? AND status = 'UNASSIGNED' AND starts_at > ?
            ORDER BY starts_at, id FOR UPDATE
            """, (row, index) -> new PendingShift(row.getLong("id"),
                row.getObject("starts_at", OffsetDateTime.class).toInstant(),
                row.getObject("ends_at", OffsetDateTime.class).toInstant(),
                row.getObject("local_date", LocalDate.class)), blockId, utc(now));
    }

    public List<Long> activeMembers() {
        return jdbc.query("SELECT id FROM member WHERE active ORDER BY id FOR SHARE",
            (row, index) -> row.getLong(1));
    }

    /** ASSIGNED shifts that ended without being marked booked become MISSED. */
    public int markMissed(Instant now) {
        return jdbc.update("UPDATE shift SET status = 'MISSED' WHERE status = 'ASSIGNED' AND ends_at <= ?", utc(now));
    }

    public Optional<PendingShift> pendingShift(long shiftId, Instant now) {
        return jdbc.query("""
            SELECT id, starts_at, ends_at, local_date FROM shift
            WHERE id = ? AND status = 'UNASSIGNED' AND starts_at > ? FOR UPDATE
            """, (row, index) -> new PendingShift(row.getLong("id"),
                row.getObject("starts_at", OffsetDateTime.class).toInstant(),
                row.getObject("ends_at", OffsetDateTime.class).toInstant(),
                row.getObject("local_date", LocalDate.class)), shiftId, utc(now)).stream().findFirst();
    }

    public List<Candidate> candidates(PendingShift shift, LocalDate weekStart) {
        return jdbc.query("""
            SELECT m.id,
                (SELECT count(*) FROM shift w WHERE w.member_id = m.id
                    AND w.status IN ('ASSIGNED', 'BOOKED')
                    AND w.local_date >= ? AND w.local_date < ?) AS weekly_shifts,
                (SELECT coalesce(sum(extract(epoch FROM w.ends_at - w.starts_at)), 0)::bigint
                    FROM shift w WHERE w.member_id = m.id AND w.status IN ('ASSIGNED', 'BOOKED')
                    AND w.local_date >= ? AND w.local_date < ?) AS weekly_seconds
            FROM member m WHERE m.active
                AND NOT EXISTS (SELECT 1 FROM shift s WHERE s.member_id = m.id
                    AND s.status IN ('ASSIGNED', 'BOOKED') AND (s.local_date = ?
                        OR (s.starts_at < ? AND s.ends_at > ?)))
            """, (row, index) -> new Candidate(row.getLong("id"), row.getLong("weekly_shifts"),
                row.getLong("weekly_seconds")), weekStart, weekStart.plusDays(7),
                weekStart, weekStart.plusDays(7), shift.localDate(), utc(shift.endsAt()), utc(shift.startsAt()));
    }

    public List<Unavailability> unavailability(long memberId) {
        return jdbc.query("""
            SELECT kind, date, weekday, valid_from, valid_until,
                extract(epoch FROM start_time)::integer AS start_seconds,
                extract(epoch FROM end_time)::integer AS end_seconds
            FROM member_unavailability WHERE member_id = ? ORDER BY id FOR SHARE
            """, (row, index) -> new Unavailability(row.getString("kind"),
                row.getObject("date", LocalDate.class), row.getObject("weekday", Integer.class),
                row.getObject("valid_from", LocalDate.class), row.getObject("valid_until", LocalDate.class),
                row.getInt("start_seconds"), row.getInt("end_seconds")), memberId);
    }

    public void assign(long shiftId, long memberId) {
        jdbc.update("UPDATE shift SET member_id = ?, status = 'ASSIGNED' WHERE id = ? AND status = 'UNASSIGNED'",
            memberId, shiftId);
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
