package com.studyroombooker.backend.controller;

import java.security.Principal;
import java.time.Instant;
import java.util.List;

import com.studyroombooker.backend.repository.ShiftRepository.RoomOption;
import com.studyroombooker.backend.service.ShiftService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShiftController {
    private final ShiftService shifts;

    public ShiftController(ShiftService shifts) {
        this.shifts = shifts;
    }

    public record ShiftResponse(long id, Instant startsAt, Instant endsAt, String status,
                                String plannedRoomCode, String memberName, String bookedRoomCode,
                                boolean assignedToMe, List<RoomOption> bookingRooms,
                                boolean libcalCancelRequired) {}

    @GetMapping("/api/shifts")
    public List<ShiftResponse> list(Principal principal) {
        return shifts.listShifts(principal == null ? null : principal.getName()).stream()
            .map(ShiftController::response).toList();
    }

    @GetMapping("/api/shifts/{id}")
    public ShiftResponse get(@PathVariable long id, Principal principal) {
        return response(shifts.getShift(id, principal == null ? null : principal.getName()));
    }

    private static ShiftResponse response(com.studyroombooker.backend.repository.ShiftRepository.ShiftSummary shift) {
        return new ShiftResponse(shift.id(), shift.startsAt(), shift.endsAt(), shift.status(),
            shift.plannedRoomCode(), shift.memberName(), shift.bookedRoomCode(),
            shift.assignedToMe(), shift.bookingRooms(), shift.libcalCancelRequired());
    }
}
