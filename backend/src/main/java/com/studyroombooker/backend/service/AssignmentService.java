package com.studyroombooker.backend.service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import com.studyroombooker.backend.repository.AssignmentRepository;
import com.studyroombooker.backend.repository.AssignmentRepository.Candidate;
import com.studyroombooker.backend.repository.AssignmentRepository.PendingShift;
import com.studyroombooker.backend.repository.AssignmentRepository.Unavailability;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignmentService {
    private static final ZoneId LIBRARY_ZONE = ZoneId.of("America/Chicago");
    private static final Comparator<Candidate> FAIRNESS = Comparator.comparingLong(Candidate::weeklyShifts)
        .thenComparingLong(Candidate::weeklySeconds).thenComparingLong(Candidate::id);
    private final AssignmentRepository assignments;
    private final Clock clock;

    public AssignmentService(AssignmentRepository assignments, Clock clock) {
        this.assignments = assignments;
        this.clock = clock;
    }

    @Transactional
    public void assignBlock(long blockId) {
        assignments.lockAssignments();
        // Keep members active and existing unavailability stable for the transaction.
        var availability = new HashMap<Long, List<Unavailability>>();
        for (long member : assignments.activeMembers()) {
            availability.put(member, assignments.unavailability(member));
        }
        for (var shift : assignments.pendingShifts(blockId, clock.instant())) {
            var weekStart = shift.localDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            // Query again after each write so earlier assignments affect eligibility and fairness.
            assignments.candidates(shift, weekStart).stream()
                .filter(candidate -> availability.containsKey(candidate.id()))
                .filter(candidate -> availability.get(candidate.id()).stream().noneMatch(rule -> overlaps(shift, rule)))
                .min(FAIRNESS)
                .ifPresent(candidate -> assignments.assign(shift.id(), candidate.id()));
        }
    }

    private static boolean overlaps(PendingShift shift, Unavailability rule) {
        LocalDate first = shift.startsAt().atZone(LIBRARY_ZONE).toLocalDate();
        LocalDate last = shift.endsAt().minusNanos(1).atZone(LIBRARY_ZONE).toLocalDate();
        for (LocalDate date = first; !date.isAfter(last); date = date.plusDays(1)) {
            boolean applies = rule.kind().equals("ONE_TIME") ? date.equals(rule.date())
                : date.getDayOfWeek().getValue() == rule.weekday()
                    && !date.isBefore(rule.validFrom())
                    && (rule.validUntil() == null || !date.isAfter(rule.validUntil()));
            if (applies) {
                Instant start = boundary(date.atStartOfDay().plusSeconds(rule.startSeconds()), false);
                Instant end = boundary(date.atStartOfDay().plusSeconds(rule.endSeconds()), true);
                if (shift.startsAt().isBefore(end) && shift.endsAt().isAfter(start)) return true;
            }
        }
        return false;
    }

    /** Repeated-hour unavailability covers both occurrences; gap boundaries clamp forward. */
    private static Instant boundary(LocalDateTime local, boolean end) {
        var rules = LIBRARY_ZONE.getRules();
        var offsets = rules.getValidOffsets(local);
        if (offsets.isEmpty()) return rules.getTransition(local).getInstant();
        return local.toInstant(end ? offsets.getLast() : offsets.getFirst());
    }
}
