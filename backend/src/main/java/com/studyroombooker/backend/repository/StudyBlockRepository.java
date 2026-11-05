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
                VALUES (?, ?, ?, ?) ON CONFLICT (study_block_id, starts_at) DO NOTHING
                """, block.id(), block.preferredRoomId(),
                OffsetDateTime.ofInstant(interval.startsAt(), ZoneOffset.UTC),
                OffsetDateTime.ofInstant(interval.endsAt(), ZoneOffset.UTC));
        }
    }
}
