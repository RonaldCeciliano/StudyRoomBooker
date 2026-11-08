package com.studyroombooker.backend.service;

import java.time.Clock;
import java.util.Objects;

import com.studyroombooker.backend.repository.ShiftBookingRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Records the assigned member's confirmation that they reserved the room on LibCal.
 * BOOKED is group tracking only; nothing here contacts or verifies LibCal.
 */
@Service
public class ShiftBookingService {
    private final ShiftBookingRepository shifts;
    private final Clock clock;

    public ShiftBookingService(ShiftBookingRepository shifts, Clock clock) {
        this.shifts = shifts;
        this.clock = clock;
    }

    /**
     * Marks an owned ASSIGNED shift BOOKED, or corrects the room of an already BOOKED one.
     * A null room keeps the current booked room, defaulting to the planned room.
     */
    @Transactional
    public void markBooked(long shiftId, String authenticatedEmail, Long bookedRoomId) {
        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to mark a shift booked");
        }
        long memberId = shifts.activeMember(authenticatedEmail).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.FORBIDDEN, "Active group membership is required"));
        var shift = shifts.lockShift(shiftId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found"));
        if (!Objects.equals(shift.memberId(), memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assigned member can mark this shift booked");
        }
        boolean bookable = shift.status().equals("ASSIGNED") || shift.status().equals("BOOKED");
        if (!bookable || !shift.endsAt().isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only your current or upcoming assigned shifts can be marked booked");
        }
        var rooms = shifts.bookableRoomIds(shift.id());
        long roomId;
        if (bookedRoomId != null) {
            if (!rooms.contains(bookedRoomId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Booked room must be the planned room or an active backup room");
            }
            roomId = bookedRoomId;
        }
        else {
            roomId = shift.bookedRoomId() != null ? shift.bookedRoomId() : rooms.getFirst();
        }
        shifts.markBooked(shift.id(), roomId, clock.instant());
    }
}
