package com.studyroombooker.backend;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.studyroombooker.backend.service.AssignmentService;
import com.studyroombooker.backend.service.StudyBlockService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@Import(AssignmentServiceTests.FixedTime.class)
class AssignmentServiceTests extends PostgresIntegrationTest {
    @TestConfiguration
    static class FixedTime {
        @Bean @Primary Clock testClock() {
            return Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneId.of("America/Chicago"));
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired AssignmentService assignments;
    @Autowired StudyBlockService blocks;
    long room;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE shift, study_block_backup_room, study_block, member_unavailability, member, room, library_hours RESTART IDENTITY CASCADE");
        room = jdbc.queryForObject("INSERT INTO room(code, name) VALUES ('A', 'Test Room') RETURNING id", Long.class);
        jdbc.execute("INSERT INTO library_hours SELECT day, time '00:00', time '24:00' FROM generate_series(1, 7) AS day");
    }

    @Test
    void processesChronologicallyWithStableMemberTieBreakAndPreservesBookedShifts() {
        long first = member(true), second = member(true), third = member(true);
        long block = block("2026-11-10");
        long late = shift(block, "2026-11-10T18:00", "2026-11-10T21:00", null, "UNASSIGNED");
        long early = shift(block, "2026-11-10T12:00", "2026-11-10T15:00", null, "UNASSIGNED");
        long middle = shift(block, "2026-11-10T15:00", "2026-11-10T18:00", null, "UNASSIGNED");
        assignments.assignBlock(block);
        assertMember(early, first);
        assertMember(middle, second);
        assertMember(late, third);
        jdbc.update("UPDATE shift SET status = 'BOOKED', booked_room_id = ?, booked_at = now() WHERE id = ?", room, early);
        var before = jdbc.queryForList("SELECT * FROM shift ORDER BY id");
        assignments.assignBlock(block);
        blocks.generateShifts(block);
        assertThat(jdbc.queryForList("SELECT * FROM shift ORDER BY id")).isEqualTo(before);
    }

    @Test
    void excludesInactiveMembersAndLeavesExcessShiftsUnassigned() {
        member(false);
        long active = member(true);
        long block = block("2026-11-10");
        long first = shift(block, "2026-11-10T12:00", "2026-11-10T15:00", null, "UNASSIGNED");
        long second = shift(block, "2026-11-10T15:00", "2026-11-10T18:00", null, "UNASSIGNED");
        assignments.assignBlock(block);
        assertMember(first, active);
        assertUnassigned(second);
        assignments.assignBlock(block);
        assertUnassigned(second);
    }

    @Test
    void excludesMembersAlreadyBookedOnTheSameChicagoDate() {
        long busy = member(true), free = member(true);
        shift(block("2026-11-10"), "2026-11-10T20:00", "2026-11-10T21:00", busy, "BOOKED");
        long block = block("2026-11-10");
        long target = shift(block, "2026-11-10T12:00", "2026-11-10T13:00", null, "UNASSIGNED");
        assignments.assignBlock(block);
        assertMember(target, free);
    }

    @Test
    void prioritizesWeeklyShiftCountBeforeHours() {
        long moreShifts = member(true), fewerShifts = member(true);
        shift(block("2026-11-09"), "2026-11-09T12:00", "2026-11-09T13:00", moreShifts, "ASSIGNED");
        shift(block("2026-11-11"), "2026-11-11T12:00", "2026-11-11T13:00", moreShifts, "ASSIGNED");
        shift(block("2026-11-09"), "2026-11-09T14:00", "2026-11-09T18:00", fewerShifts, "BOOKED");
        long block = block("2026-11-10");
        long target = shift(block, "2026-11-10T12:00", "2026-11-10T13:00", null, "UNASSIGNED");
        assignments.assignBlock(block);
        assertMember(target, fewerShifts);
    }

    @Test
    void breaksEqualWeeklyCountsByElapsedHours() {
        long moreHours = member(true), fewerHours = member(true);
        shift(block("2026-11-09"), "2026-11-09T12:00", "2026-11-09T16:00", moreHours, "ASSIGNED");
        shift(block("2026-11-09"), "2026-11-09T12:00", "2026-11-09T13:00", fewerHours, "BOOKED");
        long block = block("2026-11-10");
        long target = shift(block, "2026-11-10T12:00", "2026-11-10T13:00", null, "UNASSIGNED");
        assignments.assignBlock(block);
        assertMember(target, fewerHours);
    }

    @Test
    void usesMondayToSundayChicagoWeekRatherThanUtcOrAdjacentWeeks() {
        long first = member(true), second = member(true);
        // Sunday Chicago is already Monday UTC; this shift belongs to the previous week.
        shift(block("2026-11-08"), "2026-11-08T23:00", "2026-11-08T23:45", first, "ASSIGNED");
        shift(block("2026-11-16"), "2026-11-16T12:00", "2026-11-16T16:00", first, "ASSIGNED");
        shift(block("2026-11-09"), "2026-11-09T12:00", "2026-11-09T13:00", second, "ASSIGNED");
        long block = block("2026-11-10");
        long target = shift(block, "2026-11-10T12:00", "2026-11-10T13:00", null, "UNASSIGNED");
        assignments.assignBlock(block);
        assertMember(target, first);
    }

    @Test
    void ignoresCancelledAndMissedResponsibilitiesForDailyEligibilityAndFairness() {
        long first = member(true);
        long block = block("2026-11-10");
        shift(block, "2026-11-10T09:00", "2026-11-10T10:00", first, "CANCELLED");
        shift(block, "2026-11-10T10:00", "2026-11-10T11:00", first, "MISSED");
        long target = shift(block, "2026-11-10T12:00", "2026-11-10T13:00", null, "UNASSIGNED");
        assignments.assignBlock(block);
        assertMember(target, first);
        assertThat(jdbc.queryForList("SELECT status FROM shift ORDER BY starts_at", String.class))
            .containsExactly("CANCELLED", "MISSED", "ASSIGNED");
    }

    @Test
    void checksOneTimeAndRecurringUnavailabilityForTheWholeShift() {
        long first = member(true), second = member(true), free = member(true);
        oneTime(first, "2026-11-10", "14:45", "16:00");
        weekly(second, "2026-11-10", "2026-11-10", "12:00", "12:15");
        long block = block("2026-11-10");
        long target = shift(block, "2026-11-10T12:00", "2026-11-10T15:00", null, "UNASSIGNED");
        assignments.assignBlock(block);
        assertMember(target, free);
    }

    @Test
    void permitsTouchingAvailabilityBoundariesAndIgnoresRulesOutsideTheirDateRange() {
        long first = member(true);
        oneTime(first, "2026-11-10", "11:00", "12:00");
        oneTime(first, "2026-11-10", "15:00", "16:00");
        oneTime(first, "2026-11-11", "12:00", "15:00");
        weekly(first, "2026-11-03", "2026-11-09", "12:00", "15:00");
        weekly(first, "2026-11-11", null, "12:00", "15:00");
        long block = block("2026-11-10");
        long target = shift(block, "2026-11-10T12:00", "2026-11-10T15:00", null, "UNASSIGNED");
        assignments.assignBlock(block);
        assertMember(target, first);
    }

    @Test
    void detectsActiveOverlapAcrossMidnightEvenWhenLocalDatesDiffer() {
        long busy = member(true), free = member(true);
        shift(block("2026-11-09"), "2026-11-09T23:00", "2026-11-10T00:30", busy, "ASSIGNED");
        long block = block("2026-11-10");
        long target = shift(block, "2026-11-10T00:00", "2026-11-10T01:00", null, "UNASSIGNED");
        assignments.assignBlock(block);
        assertMember(target, free);
    }

    @Test
    void checksUnavailabilityOnBothDatesOfAnOvernightShiftIncludingMidnightClosing() {
        long first = member(true), second = member(true);
        oneTime(first, "2026-11-10", "00:15", "00:30");
        oneTime(second, "2026-11-09", "23:45", "24:00");
        long block = block("2026-11-09");
        long target = shift(block, "2026-11-09T23:00", "2026-11-10T01:00", null, "UNASSIGNED");
        assignments.assignBlock(block);
        assertUnassigned(target);
    }

    @Test
    void coversBothOccurrencesOfUnavailableTimeDuringFallBack() {
        long member = member(true);
        oneTime(member, "2027-11-07", "01:15", "01:45");
        long block = block("2027-11-07");
        long early = instantShift(block, "2027-11-07T06:00:00Z", "2027-11-07T06:30:00Z", null, "UNASSIGNED");
        long late = instantShift(block, "2027-11-07T07:00:00Z", "2027-11-07T07:30:00Z", null, "UNASSIGNED");
        assignments.assignBlock(block);
        assertUnassigned(early);
        assertUnassigned(late);
    }

    @Test
    void clampsNonexistentSpringForwardUnavailabilityBoundaryToTheTransition() {
        long member = member(true);
        oneTime(member, "2027-03-14", "02:15", "03:15");
        long block = block("2027-03-14");
        long target = instantShift(block, "2027-03-14T08:00:00Z", "2027-03-14T08:30:00Z", null, "UNASSIGNED");
        assignments.assignBlock(block);
        assertUnassigned(target);
    }

    @Test
    void leavesPastAndStartedShiftsUntouched() {
        member(true);
        long block = block("2026-11-05");
        long past = shift(block, "2026-11-05T04:00", "2026-11-05T05:00", null, "UNASSIGNED");
        long started = shift(block, "2026-11-05T06:00", "2026-11-05T07:00", null, "UNASSIGNED");
        assignments.assignBlock(block);
        assertUnassigned(past);
        assertUnassigned(started);
    }

    @Test
    void concurrentCreatesChooseDifferentMembersWithoutViolatingDailyLimits() throws Exception {
        member(true);
        member(true);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var tasks = java.util.stream.IntStream.range(0, 2).mapToObj(index -> executor.submit(() -> {
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start barrier timed out");
                return blocks.create(new StudyBlockService.CreateRequest(LocalDate.parse("2026-11-10"),
                    java.time.LocalTime.NOON, java.time.LocalTime.of(13, 0), room, List.of()));
            })).toList();
            start.countDown();
            for (var task : tasks) assertThat(task.get(15, TimeUnit.SECONDS).id()).isPositive();
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM shift WHERE status = 'ASSIGNED'", Integer.class)).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(DISTINCT member_id) FROM shift", Integer.class)).isEqualTo(2);
    }

    private long member(boolean active) {
        String name = "member-" + java.util.UUID.randomUUID();
        return jdbc.queryForObject("INSERT INTO member(name, email, active) VALUES (?, ?, ?) RETURNING id",
            Long.class, name, name + "@example.test", active);
    }

    private long block(String date) {
        return jdbc.queryForObject("INSERT INTO study_block(kind, date, start_time, end_time, preferred_room_id) VALUES ('ONE_TIME', ?::date, '12:00', '21:00', ?) RETURNING id", Long.class, date, room);
    }

    private long shift(long block, String start, String end, Long member, String status) {
        return instantShift(block, LocalDateTime.parse(start).atZone(ZoneId.of("America/Chicago")).toInstant().toString(),
            LocalDateTime.parse(end).atZone(ZoneId.of("America/Chicago")).toInstant().toString(), member, status);
    }

    private long instantShift(long block, String start, String end, Long member, String status) {
        return jdbc.queryForObject("""
            INSERT INTO shift(study_block_id, planned_room_id, starts_at, ends_at, member_id, status, booked_room_id, booked_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
            """, Long.class, block, room, OffsetDateTime.parse(start), OffsetDateTime.parse(end), member, status,
            status.equals("BOOKED") ? room : null, status.equals("BOOKED") ? OffsetDateTime.now() : null);
    }

    private void oneTime(long member, String date, String start, String end) {
        jdbc.update("INSERT INTO member_unavailability(member_id, kind, date, start_time, end_time) VALUES (?, 'ONE_TIME', ?::date, ?::time, ?::time)", member, date, start, end);
    }

    private void weekly(long member, String from, String until, String start, String end) {
        jdbc.update("INSERT INTO member_unavailability(member_id, kind, weekday, valid_from, valid_until, start_time, end_time) VALUES (?, 'WEEKLY', 2, ?::date, ?::date, ?::time, ?::time)", member, from, until, start, end);
    }

    private void assertMember(long shift, long member) {
        assertThat(jdbc.queryForObject("SELECT member_id FROM shift WHERE id = ?", Long.class, shift)).isEqualTo(member);
        assertThat(jdbc.queryForObject("SELECT status FROM shift WHERE id = ?", String.class, shift)).isEqualTo("ASSIGNED");
    }

    private void assertUnassigned(long shift) {
        assertThat(jdbc.queryForObject("SELECT member_id FROM shift WHERE id = ?", Long.class, shift)).isNull();
        assertThat(jdbc.queryForObject("SELECT status FROM shift WHERE id = ?", String.class, shift)).isEqualTo("UNASSIGNED");
    }
}
