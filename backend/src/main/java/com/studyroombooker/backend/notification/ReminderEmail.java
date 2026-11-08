package com.studyroombooker.backend.notification;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import com.studyroombooker.backend.email.EmailMessage;

/** Booking reminder content from ARCHITECTURE.md "Booking Reminder". */
final class ReminderEmail {
    static final String LIBCAL_RESERVE_URL = "https://uark.libcal.com/reserve";
    private static final ZoneId LIBRARY_ZONE = ZoneId.of("America/Chicago");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, MMMM d", Locale.US);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.US).withZone(LIBRARY_ZONE);

    private ReminderEmail() {}

    static EmailMessage of(NotificationRepository.Due due, String appUrl) {
        String date = DATE.format(due.date());
        String backups = due.backupRoomCodes().isEmpty() ? "None" : String.join(", ", due.backupRoomCodes());
        return new EmailMessage(due.email(), "Book your study room for " + date, """
            Study Room Booker

            Room: %s
            Backup Rooms: %s

            Date: %s
            Time: %s - %s

            Book Room: %s

            This date is now within LibCal's 14-day booking window. If the time slot is not
            open yet, try again later today.

            After booking, mark the shift booked in StudyRoomBooker: %s
            """.formatted(due.roomCode(), backups, date, TIME.format(due.startsAt()), TIME.format(due.endsAt()),
                LIBCAL_RESERVE_URL, appUrl));
    }
}
