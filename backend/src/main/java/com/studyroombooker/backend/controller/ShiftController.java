package com.studyroombooker.backend.controller;

import java.time.Instant;
import java.util.List;

import com.studyroombooker.backend.service.ShiftService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShiftController {
    private final ShiftService shifts;

    public ShiftController(ShiftService shifts) {
        this.shifts = shifts;
    }

    public record ShiftResponse(long id, Instant startsAt, Instant endsAt, String status,
                                String plannedRoomCode, String memberName) {}

    @GetMapping("/api/shifts")
    public List<ShiftResponse> list() {
        return shifts.listShifts().stream().map(shift -> new ShiftResponse(shift.id(),
            shift.startsAt(), shift.endsAt(), shift.status(), shift.plannedRoomCode(),
            shift.memberName())).toList();
    }
}
