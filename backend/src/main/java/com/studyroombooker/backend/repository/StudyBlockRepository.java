package com.studyroombooker.backend.repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.studyroombooker.backend.service.ShiftSplitter.Interval;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class StudyBlockRepository {
    private final JdbcTemplate jdbc;

    public StudyBlockRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public record Block(long id, LocalDate date, LocalTime startTime, LocalTime endTime,
                        long preferredRoomId) {}
    public record Hours(int opensAtSeconds, int closesAtSeconds) {}

    public boolean activeRoom(long id) {
        return !jdbc.query("SELECT id FROM room WHERE id = ? AND active FOR SHARE",
            (row, index) -> row.getLong(1), id).isEmpty();
    }

    public Optional<Hours> hours(int weekday) {
        return jdbc.query("""
            SELECT extract(epoch FROM opens_at)::integer, extract(epoch FROM closes_at)::integer
            FROM library_hours WHERE weekday = ? FOR SHARE
            """, (row, index) -> new Hours(row.getInt(1), row.getInt(2)), weekday).stream().findFirst();
    }

    public long insert(LocalDate date, LocalTime start, LocalTime end, long room, List<Long> backups) {
        long id = jdbc.queryForObject("""
            INSERT INTO study_block(kind, date, start_time, end_time, preferred_room_id)
            VALUES ('ONE_TIME', ?, ?, ?, ?) RETURNING id
            """, Long.class, date, start, end, room);
        for (int i = 0; i < backups.size(); i++) {
            jdbc.update("INSERT INTO study_block_backup_room VALUES (?, ?, ?)", id, backups.get(i), i + 1);
        }
        return id;
    }

    public record BlockView(long id, LocalDate date, LocalTime startTime, LocalTime endTime,
                            long preferredRoomId, List<Long> backupRoomIds) {}

    public record Room(long id, String code, String name) {}

    public List<Room> activeRooms() {
        return jdbc.query("SELECT id, code, name FROM room WHERE active ORDER BY code",
            (row, index) -> new Room(row.getLong("id"), row.getString("code"), row.getString("name")));
    }

    /** One-time blocks on or after {@code from} (a library-local date), soonest first. */
    public List<BlockView> upcoming(LocalDate from) {
        return jdbc.query("""
            SELECT b.id, b.date, b.start_time, b.end_time, b.preferred_room_id,
                   coalesce(array_agg(br.room_id ORDER BY br.priority) FILTER (WHERE br.room_id IS NOT NULL), '{}') AS backups
            FROM study_block b
            LEFT JOIN study_block_backup_room br ON br.study_block_id = b.id
            WHERE b.kind = 'ONE_TIME' AND b.date >= ?
            GROUP BY b.id
            ORDER BY b.date, b.start_time, b.id
            """, (row, index) -> new BlockView(row.getLong("id"), row.getObject("date", LocalDate.class),
                row.getObject("start_time", LocalTime.class), row.getObject("end_time", LocalTime.class),
                row.getLong("preferred_room_id"),
                List.of((Long[]) row.getArray("backups").getArray())), from);
    }

    public void update(long id, LocalDate date, LocalTime start, LocalTime end, long room, List<Long> backups) {
        jdbc.update("UPDATE study_block SET date = ?, start_time = ?, end_time = ?, preferred_room_id = ? WHERE id = ?",
            date, start, end, room, id);
        jdbc.update("DELETE FROM study_block_backup_room WHERE study_block_id = ?", id);
        for (int i = 0; i < backups.size(); i++) {
            jdbc.update("INSERT INTO study_block_backup_room VALUES (?, ?, ?)", id, backups.get(i), i + 1);
        }
    }

    /**
     * Times changed: BOOKED shifts become CANCELLED and flagged for LibCal cancellation
     * (the real reservation still exists); unbooked shifts are removed for regeneration.
     */
    public void clearShiftsForRegeneration(long blockId) {
        jdbc.update("""
            UPDATE shift SET status = 'CANCELLED', libcal_cancel_required = true
            WHERE study_block_id = ? AND status = 'BOOKED'
            """, blockId);
        jdbc.update("DELETE FROM shift WHERE study_block_id = ? AND status IN ('UNASSIGNED', 'ASSIGNED')", blockId);
    }

    /** Times unchanged: unbooked shifts follow the new preferred room; booked ones keep their reservation. */
    public void replanRoom(long blockId, long room) {
        jdbc.update("UPDATE shift SET planned_room_id = ? WHERE study_block_id = ? AND status IN ('UNASSIGNED', 'ASSIGNED')",
            room, blockId);
    }

    public Optional<Block> findOneTime(long id) {
        return jdbc.query("SELECT * FROM study_block WHERE id = ? AND kind = 'ONE_TIME' FOR UPDATE",
            (row, index) -> new Block(row.getLong("id"), row.getObject("date", LocalDate.class),
                row.getObject("start_time", LocalTime.class), row.getObject("end_time", LocalTime.class),
                row.getLong("preferred_room_id")), id).stream().findFirst();
    }

    public void insertShifts(Block block, List<Interval> intervals) {
        for (var interval : intervals) {
            jdbc.update("""
                INSERT INTO shift(study_block_id, planned_room_id, starts_at, ends_at)
                VALUES (?, ?, ?, ?) ON CONFLICT (study_block_id, starts_at) WHERE status <> 'CANCELLED' DO NOTHING
                """, block.id(), block.preferredRoomId(),
                OffsetDateTime.ofInstant(interval.startsAt(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(interval.endsAt(), ZoneOffset.UTC));
        }
    }
}
