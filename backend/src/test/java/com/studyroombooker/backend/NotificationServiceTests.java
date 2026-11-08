package com.studyroombooker.backend;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import com.studyroombooker.backend.email.EmailDeliveryException;
import com.studyroombooker.backend.email.EmailMessage;
import com.studyroombooker.backend.email.EmailSender;
import com.studyroombooker.backend.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = "app.base-url=https://app.example.test")
@Import(NotificationServiceTests.Fakes.class)
class NotificationServiceTests extends PostgresIntegrationTest {
    // Thursday, November 5, 2026, 6:00 AM Chicago.
    static final Instant START = Instant.parse("2026-11-05T12:00:00Z");

    @TestConfiguration
    static class Fakes {
        static final MutableClock CLOCK = new MutableClock();
        static final List<EmailMessage> SENT = new CopyOnWriteArrayList<>();
        static volatile boolean failing;

        @Bean @Primary Clock testClock() { return CLOCK; }
        @Bean @Primary EmailSender fakeSender() {
            return message -> {
                if (failing) throw new EmailDeliveryException("SMTP down", new RuntimeException("connection refused"));
                SENT.add(message);
            };
        }
    }

    static final class MutableClock extends Clock {
        volatile Instant now = START;
        @Override public ZoneId getZone() { return ZoneId.of("America/Chicago"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
        void advance(Duration duration) { now = now.plus(duration); }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired NotificationService notifications;

    @BeforeEach
    void seed() {
        jdbc.execute("TRUNCATE notification, shift, study_block_backup_room, study_block, member_unavailability, member, room, library_hours RESTART IDENTITY CASCADE");
        Fakes.CLOCK.now = START;
        Fakes.SENT.clear();
        Fakes.failing = false;
        jdbc.update("INSERT INTO member(name, email) VALUES ('One', 'one@example.test'), ('Two', 'two@example.test')");
        jdbc.update("INSERT INTO room(code, name) VALUES ('441D', 'D'), ('441E', 'E'), ('441C', 'C')");
    }

    /** A 6:00–10:00 PM Chicago shift on {@code date}, assigned to {@code member}. */
    long shift(String date, Long member, String status) {
        long block = jdbc.queryForObject("INSERT INTO study_block(kind, date, start_time, end_time, preferred_room_id) VALUES ('ONE_TIME', ?::date, '18:00', '22:00', 1) RETURNING id", Long.class, date);
        jdbc.update("INSERT INTO study_block_backup_room VALUES (?, 2, 1), (?, 3, 2)", block, block);
        return jdbc.queryForObject("""
            INSERT INTO shift(study_block_id, planned_room_id, starts_at, ends_at, member_id, status)
            VALUES (?, 1, (?::date + time '18:00') AT TIME ZONE 'America/Chicago', (?::date + time '22:00') AT TIME ZONE 'America/Chicago', ?, ?)
            RETURNING id
            """, Long.class, block, date, date, member, status);
    }

    @Test
    void remindsOnceWhenTheShiftDateEntersTheFourteenDayWindow() {
        shift("2026-11-20", 1L, "ASSIGNED"); // 15 days ahead: not yet bookable

        assertThat(notifications.runOnce().enqueued()).isZero();
        assertThat(Fakes.SENT).isEmpty();

        Fakes.CLOCK.now = Instant.parse("2026-11-06T06:00:00Z"); // 12:00 AM Chicago, Nov 6: 14 days ahead
        var result = notifications.runOnce();
        assertThat(result.enqueued()).isEqualTo(1);
        assertThat(result.sent()).isEqualTo(1);

        EmailMessage reminder = Fakes.SENT.getFirst();
        assertThat(reminder.to()).isEqualTo("one@example.test");
        assertThat(reminder.subject()).isEqualTo("Book your study room for Friday, November 20");
        assertThat(reminder.body())
            .contains("Room: 441D")
            .contains("Backup Rooms: 441E, 441C")
            .contains("Date: Friday, November 20")
            .contains("Time: 6:00 PM - 10:00 PM")
            .contains("Book Room: https://uark.libcal.com/reserve")
            .contains("https://app.example.test/");

        Fakes.CLOCK.advance(Duration.ofMinutes(5));
        assertThat(notifications.runOnce()).isEqualTo(new NotificationService.RunResult(0, 0, 0, 0));
        assertThat(Fakes.SENT).hasSize(1);
        var row = jdbc.queryForMap("SELECT status, attempt_count, sent_at FROM notification");
        assertThat(row.get("status")).isEqualTo("SENT");
        assertThat(row.get("attempt_count")).isEqualTo(1);
    }

    @Test
    void onlyFutureAssignedShiftsGetReminders() {
        shift("2026-11-10", 1L, "ASSIGNED");
        long booked = shift("2026-11-11", 1L, "ASSIGNED");
        jdbc.update("UPDATE shift SET status = 'BOOKED', booked_room_id = 1, booked_at = now() WHERE id = ?", booked);
        shift("2026-11-12", null, "UNASSIGNED");
        shift("2026-11-04", 2L, "ASSIGNED"); // past

        assertThat(notifications.runOnce().sent()).isEqualTo(1);
        assertThat(Fakes.SENT).extracting(EmailMessage::subject).containsExactly("Book your study room for Tuesday, November 10");
    }

    @Test
    void concurrentRunsSendEachReminderOnce() throws Exception {
        for (String date : new String[] {"2026-11-10", "2026-11-11", "2026-11-12"}) {
            shift(date, 1L, "ASSIGNED");
        }
        var ready = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(4)) {
            var runs = java.util.stream.IntStream.range(0, 4)
                .mapToObj(i -> pool.submit(() -> { ready.await(); return notifications.runOnce(); })).toList();
            ready.countDown();
            for (var run : runs) run.get();
        }
        assertThat(Fakes.SENT).hasSize(3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM notification WHERE status = 'SENT'", Integer.class)).isEqualTo(3);
    }

    @Test
    void failedSendsStayRetryableWithBackoffUntilTheySucceed() {
        shift("2026-11-10", 1L, "ASSIGNED");
        Fakes.failing = true;

        assertThat(notifications.runOnce().failed()).isEqualTo(1);
        var failed = jdbc.queryForMap("SELECT status, attempt_count, last_error, next_attempt_at FROM notification");
        assertThat(failed.get("status")).isEqualTo("FAILED");
        assertThat(failed.get("attempt_count")).isEqualTo(1);
        assertThat((String) failed.get("last_error")).contains("SMTP down");
        assertThat(((java.sql.Timestamp) failed.get("next_attempt_at")).toInstant()).isEqualTo(START.plus(Duration.ofMinutes(5)));

        Fakes.failing = false;
        Fakes.CLOCK.advance(Duration.ofMinutes(4));
        assertThat(notifications.runOnce().sent()).isZero(); // backoff not elapsed

        Fakes.CLOCK.advance(Duration.ofMinutes(1));
        assertThat(notifications.runOnce().sent()).isEqualTo(1);
        var sent = jdbc.queryForMap("SELECT status, attempt_count, last_error, next_attempt_at FROM notification");
        assertThat(sent.get("status")).isEqualTo("SENT");
        assertThat(sent.get("attempt_count")).isEqualTo(2);
        assertThat(sent.get("last_error")).isNull();
        assertThat(sent.get("next_attempt_at")).isNull();
    }

    @Test
    void stopsRetryingAfterFiveAttempts() {
        shift("2026-11-10", 1L, "ASSIGNED");
        Fakes.failing = true;
        for (int i = 0; i < 8; i++) {
            notifications.runOnce();
            Fakes.CLOCK.advance(Duration.ofHours(3));
        }
        var row = jdbc.queryForMap("SELECT status, attempt_count, next_attempt_at FROM notification");
        assertThat(row.get("status")).isEqualTo("FAILED");
        assertThat(row.get("attempt_count")).isEqualTo(5);
        assertThat(row.get("next_attempt_at")).isNull();
    }

    @Test
    void reassignedShiftsRemindTheNewOwnerAndSkipTheStaleReminder() {
        long id = shift("2026-11-10", 1L, "ASSIGNED");
        Fakes.failing = true;
        notifications.runOnce(); // One's reminder failed and is waiting to retry

        jdbc.update("UPDATE shift SET member_id = 2 WHERE id = ?", id);
        Fakes.failing = false;
        Fakes.CLOCK.advance(Duration.ofMinutes(5));
        var result = notifications.runOnce();

        assertThat(result.skipped()).isEqualTo(1);
        assertThat(result.sent()).isEqualTo(1);
        assertThat(Fakes.SENT).extracting(EmailMessage::to).containsExactly("two@example.test");
        assertThat(jdbc.queryForObject("SELECT next_attempt_at IS NULL FROM notification WHERE member_id = 1", Boolean.class)).isTrue();
    }
}
