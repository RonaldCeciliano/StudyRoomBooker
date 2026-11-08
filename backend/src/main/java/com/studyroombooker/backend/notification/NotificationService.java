package com.studyroombooker.backend.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;

import com.studyroombooker.backend.action.ActionTokenService;
import com.studyroombooker.backend.email.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Self-healing outbox processing: each run works out what is due now rather than assuming
 * it ran at an exact time. Safe to run repeatedly or concurrently.
 *
 * <p>Delivery is at-least-once: if the process dies after the email is accepted but before
 * the row is marked SENT, that reminder can be sent again on the next run.
 */
@Service
public class NotificationService {
    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final ZoneId LIBRARY_ZONE = ZoneId.of("America/Chicago");
    static final int BOOKING_WINDOW_DAYS = 14;
    static final int MAX_ATTEMPTS = 5;
    static final Duration FIRST_RETRY = Duration.ofMinutes(5);

    private final NotificationRepository notifications;
    private final EmailSender emailSender;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final String appUrl;
    private final ActionTokenService actionTokens;

    public NotificationService(NotificationRepository notifications, EmailSender emailSender,
            TransactionTemplate transactions, Clock clock, @Value("${app.base-url:}") String appUrl,
            ActionTokenService actionTokens) {
        this.actionTokens = actionTokens;
        this.notifications = notifications;
        this.emailSender = emailSender;
        this.transactions = transactions;
        this.clock = clock;
        this.appUrl = appUrl.replaceAll("/+$", "") + "/";
    }

    public record RunResult(int enqueued, int sent, int failed, int skipped) {}

    public RunResult runOnce() {
        var now = clock.instant();
        // A date becomes bookable 14 days ahead. Whether LibCal opens it at midnight or at the
        // matching time of day is unverified (ADR 0005), so remind from the start of that day.
        LocalDate lastBookableDate = LocalDate.ofInstant(now, LIBRARY_ZONE).plusDays(BOOKING_WINDOW_DAYS);
        Integer enqueued = transactions.execute(status -> notifications.enqueueReminders(lastBookableDate, now));
        int sent = 0, failed = 0, skipped = 0;
        for (long id : notifications.dueIds(now, 100)) {
            Outcome outcome = transactions.execute(status -> deliver(id));
            if (outcome == Outcome.SENT) sent++;
            else if (outcome == Outcome.FAILED) failed++;
            else if (outcome == Outcome.SKIPPED) skipped++;
        }
        return new RunResult(enqueued == null ? 0 : enqueued, sent, failed, skipped);
    }

    private enum Outcome { SENT, FAILED, SKIPPED, NONE }

    private Outcome deliver(long id) {
        var now = clock.instant();
        var claimed = notifications.claim(id, now);
        if (claimed.isEmpty()) {
            return Outcome.NONE; // already handled, or another run holds it
        }
        var due = claimed.get();
        if (!due.stillRelevant()) {
            // Released, booked, cancelled, started, or member deactivated since it was queued.
            notifications.markFailed(id, "Not sent: the shift is no longer assigned to this member", null, false);
            return Outcome.SKIPPED;
        }
        // The "I booked it" link is valid until the shift ends.
        String token = actionTokens.issueMarkBooked(due.memberId(), due.shiftId(), due.endsAt());
        try {
            emailSender.send(ReminderEmail.of(due, appUrl, token));
            notifications.markSent(id, now);
            return Outcome.SENT;
        }
        catch (RuntimeException ex) {
            actionTokens.revoke(token); // the link was never delivered
            int attempts = due.attemptCount() + 1;
            var retryAt = attempts >= MAX_ATTEMPTS ? null : now.plus(FIRST_RETRY.multipliedBy(1L << (attempts - 1)));
            String error = ex.getClass().getSimpleName() + ": " + String.valueOf(ex.getMessage());
            notifications.markFailed(id, error.length() > 500 ? error.substring(0, 500) : error, retryAt, true);
            log.warn("Reminder {} failed (attempt {} of {})", id, attempts, MAX_ATTEMPTS, ex);
            return Outcome.FAILED;
        }
    }
}
