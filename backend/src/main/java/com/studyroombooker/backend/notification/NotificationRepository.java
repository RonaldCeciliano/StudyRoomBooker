package com.studyroombooker.backend.notification;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {
    private final JdbcTemplate jdbc;

    public NotificationRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** A claimed notification with everything needed to write the reminder. */
    public record Due(long id, long memberId, long shiftId, int attemptCount, boolean stillRelevant, String email, LocalDate date,
                      Instant startsAt, Instant endsAt, String roomCode, List<String> backupRoomCodes) {}

    /**
     * Enqueues a REMINDER for every future ASSIGNED shift whose library-local date is within
     * the booking window. Idempotent: the unique (shift, member, type) rule skips existing rows.
     */
    public int enqueueReminders(LocalDate lastBookableDate, Instant now) {
        return jdbc.update("""
            INSERT INTO notification(shift_id, member_id, type, status, next_attempt_at, created_at)
            SELECT s.id, s.member_id, 'REMINDER', 'PENDING', ?, ?
            FROM shift s
            WHERE s.status = 'ASSIGNED' AND s.member_id IS NOT NULL
              AND s.starts_at > ? AND s.local_date <= ?
            ON CONFLICT (shift_id, member_id, type) DO NOTHING
            """, Timestamp.from(now), Timestamp.from(now), Timestamp.from(now), lastBookableDate);
    }

    public List<Long> dueIds(Instant now, int limit) {
        return jdbc.queryForList("""
            SELECT id FROM notification
            WHERE status IN ('PENDING', 'FAILED') AND next_attempt_at <= ?
            ORDER BY next_attempt_at, id LIMIT ?
            """, Long.class, Timestamp.from(now), limit);
    }

    /**
     * Locks one due notification, skipping rows another run holds, and re-checks that it is
     * still due. Empty when it was already handled or is locked elsewhere.
     */
    public Optional<Due> claim(long id, Instant now) {
        return jdbc.query("""
            SELECT n.id, n.member_id, n.shift_id, n.attempt_count, m.email, s.local_date, s.starts_at, s.ends_at, r.code,
                   (s.status = 'ASSIGNED' AND s.member_id = n.member_id AND m.active AND s.starts_at > ?) AS relevant,
                   coalesce((SELECT array_agg(br.code ORDER BY b.priority)
                             FROM study_block_backup_room b JOIN room br ON br.id = b.room_id AND br.active
                             WHERE b.study_block_id = s.study_block_id), '{}') AS backups
            FROM notification n
            JOIN shift s ON s.id = n.shift_id
            JOIN member m ON m.id = n.member_id
            JOIN room r ON r.id = s.planned_room_id
            WHERE n.id = ? AND n.status IN ('PENDING', 'FAILED') AND n.next_attempt_at <= ?
            FOR UPDATE OF n SKIP LOCKED
            """, (row, index) -> new Due(row.getLong("id"), row.getLong("member_id"), row.getLong("shift_id"), row.getInt("attempt_count"), row.getBoolean("relevant"),
                row.getString("email"), row.getObject("local_date", LocalDate.class),
                row.getObject("starts_at", OffsetDateTime.class).toInstant(),
                row.getObject("ends_at", OffsetDateTime.class).toInstant(), row.getString("code"),
                List.of((String[]) row.getArray("backups").getArray())),
            Timestamp.from(now), id, Timestamp.from(now)).stream().findFirst();
    }

    public void markSent(long id, Instant now) {
        jdbc.update("""
            UPDATE notification SET status = 'SENT', sent_at = ?, attempt_count = attempt_count + 1,
                last_error = NULL, next_attempt_at = NULL WHERE id = ?
            """, Timestamp.from(now), id);
    }

    /** {@code retryAt} null stops further attempts. */
    public void markFailed(long id, String error, Instant retryAt, boolean countAttempt) {
        jdbc.update("""
            UPDATE notification SET status = 'FAILED', last_error = ?, next_attempt_at = ?,
                attempt_count = attempt_count + ? WHERE id = ?
            """, error, retryAt == null ? null : Timestamp.from(retryAt), countAttempt ? 1 : 0, id);
    }

}
