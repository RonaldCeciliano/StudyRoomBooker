package com.studyroombooker.backend.service;

import java.time.Clock;

import com.studyroombooker.backend.repository.AssignmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Marks ASSIGNED shifts MISSED once they end without the member marking them booked.
 * The end (not the start) is used because members may confirm a booking until the shift
 * ends. As already established for assignment (#53), MISSED shifts are not counted
 * toward weekly fairness or the one-per-day limit.
 */
@Service
public class MissedShiftService {
    private final AssignmentRepository assignments;
    private final Clock clock;

    public MissedShiftService(AssignmentRepository assignments, Clock clock) {
        this.assignments = assignments;
        this.clock = clock;
    }

    @Transactional
    public int markOverdue() {
        assignments.lockAssignments();
        return assignments.markMissed(clock.instant());
    }
}
