package com.studyroombooker.backend.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class UnavailabilityRepository {
    private final JdbcTemplate jdbc;

    public UnavailabilityRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    /** Times are HH:MM strings so an end of 24:00 (end of day) round-trips. */
    public record Entry(long id, String kind, LocalDate date, Integer weekday, LocalDate validFrom,
                        LocalDate validUntil, String startTime, String endTime) {}

    private static final String COLUMNS = """
        id, kind, date, weekday, valid_from, valid_until,
        to_char(start_time, 'HH24:MI') AS start_time, to_char(end_time, 'HH24:MI') AS end_time
        """;

    /** Share-locks the member row when {@code lock} is set, for writes; reads stay lock-free. */
    public Optional<Long> activeMember(String email, boolean lock) {
        return jdbc.query("SELECT id FROM member WHERE active AND lower(email) = lower(?)" + (lock ? " FOR SHARE" : ""),
            (row, index) -> row.getLong(1), email).stream().findFirst();
    }

    public List<Entry> forMember(long memberId) {
        return jdbc.query("SELECT " + COLUMNS + """
             FROM member_unavailability WHERE member_id = ?
            ORDER BY kind, coalesce(date, valid_from), weekday, start_time, id
            """, (row, index) -> map(row), memberId);
    }

    public Entry insert(long memberId, String kind, LocalDate date, Integer weekday, LocalDate validFrom,
                        LocalDate validUntil, String startTime, String endTime) {
        return jdbc.queryForObject("""
            INSERT INTO member_unavailability(member_id, kind, date, weekday, valid_from, valid_until, start_time, end_time)
            VALUES (?, ?, ?, ?, ?, ?, ?::time, ?::time) RETURNING
            """ + COLUMNS, (row, index) -> map(row),
            memberId, kind, date, weekday, validFrom, validUntil, startTime, endTime);
    }

    public boolean delete(long memberId, long id) {
        return jdbc.update("DELETE FROM member_unavailability WHERE id = ? AND member_id = ?", id, memberId) == 1;
    }

    private static Entry map(java.sql.ResultSet row) throws java.sql.SQLException {
        return new Entry(row.getLong("id"), row.getString("kind"), row.getObject("date", LocalDate.class),
            row.getObject("weekday", Integer.class), row.getObject("valid_from", LocalDate.class),
            row.getObject("valid_until", LocalDate.class), row.getString("start_time"), row.getString("end_time"));
    }
}
