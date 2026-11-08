package com.studyroombooker.backend.notification;

import com.studyroombooker.backend.service.MissedShiftService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Marks ended unbooked shifts MISSED, then runs the outbox, every {@code app.notifications.interval} (5 minutes by default). One
 * backend instance is assumed (ARCHITECTURE.md); the outbox is still safe if runs overlap.
 * Disabled with {@code app.scheduler.enabled=false} (as in tests).
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduler.enabled", havingValue = "true", matchIfMissing = true)
public class NotificationScheduler {
    private static final Logger log = LoggerFactory.getLogger(NotificationScheduler.class);
    private final NotificationService notifications;
    private final MissedShiftService missedShifts;

    public NotificationScheduler(NotificationService notifications, MissedShiftService missedShifts) {
        this.notifications = notifications;
        this.missedShifts = missedShifts;
    }

    @Scheduled(fixedDelayString = "${app.notifications.interval:5m}", initialDelayString = "${app.notifications.initial-delay:30s}")
    public void run() {
        try {
            int missed = missedShifts.markOverdue();
            if (missed > 0) {
                log.info("Marked {} ended unbooked shift(s) MISSED", missed);
            }
        }
        catch (RuntimeException ex) {
            log.error("Marking missed shifts failed; the next run will retry", ex);
        }
        try {
            var result = notifications.runOnce();
            if (result.enqueued() + result.sent() + result.failed() + result.skipped() > 0) {
                log.info("Notifications: {}", result);
            }
        }
        catch (RuntimeException ex) {
            log.error("Notification run failed; the next run will retry", ex);
        }
    }
}
