package com.studyroombooker.backend.service;

import java.time.Clock;
import java.util.Objects;

import com.studyroombooker.backend.repository.AssignmentRepository;
import com.studyroombooker.backend.repository.ShiftReleaseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShiftReleaseService {
    private final AssignmentRepository assignments;
    private final ShiftReleaseRepository shifts;
    private final AssignmentService assignmentService;
    private final Clock clock;

    public ShiftReleaseService(AssignmentRepository assignments, ShiftReleaseRepository shifts,
                               AssignmentService assignmentService, Clock clock) {
        this.assignments = assignments;
        this.shifts = shifts;
        this.assignmentService = assignmentService;
        this.clock = clock;
    }

    /** The email must come from the authenticated principal, never client-supplied identity. */
    @Transactional
    public void release(long shiftId, String authenticatedEmail) {
        if (authenticatedEmail == null || authenticatedEmail.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to release a shift");
        }
        assignments.lockAssignments();
        long memberId = shifts.activeMember(authenticatedEmail).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.FORBIDDEN, "Active group membership is required"));
        var shift = shifts.lockShift(shiftId).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Shift not found"));
        if (!Objects.equals(shift.memberId(), memberId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only the assigned member can release this shift");
        }
        if (!shift.status().equals("ASSIGNED") || !shift.startsAt().isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only future, unbooked assignments can be released");
        }
        shifts.release(shift.id());
        assignmentService.assignReleasedShift(shift.id(), memberId);
    }
}
