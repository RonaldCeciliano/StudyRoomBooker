package com.studyroombooker.backend.service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.regex.Pattern;

import com.studyroombooker.backend.repository.AssignmentRepository;
import com.studyroombooker.backend.repository.UnavailabilityRepository;
import com.studyroombooker.backend.repository.UnavailabilityRepository.Entry;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * The signed-in member's own one-time and weekly unavailability. Changes take the shared
 * assignment lock because they are assignment inputs. They affect future assignment
 * only; shifts already assigned stay assigned (the member can release them).
 */
@Service
public class UnavailabilityService {
    private static final ZoneId LIBRARY_ZONE = ZoneId.of("America/Chicago");
    private static final Pattern TIME = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");

    private final UnavailabilityRepository entries;
    private final AssignmentRepository assignments;
    private final Clock clock;

    public UnavailabilityService(UnavailabilityRepository entries, AssignmentRepository assignments, Clock clock) {
        this.entries = entries;
        this.assignments = assignments;
        this.clock = clock;
    }

    public record CreateRequest(String kind, LocalDate date, Integer weekday, LocalDate validFrom,
                                LocalDate validUntil, String startTime, String endTime) {}

    @Transactional(readOnly = true)
    public List<Entry> list(String email) {
        return entries.forMember(member(email, false));
    }

    @Transactional
    public Entry create(String email, CreateRequest request) {
        assignments.lockAssignments();
        long memberId = member(email, true);
        require(request.startTime() != null && TIME.matcher(request.startTime()).matches(), "startTime must be HH:MM");
        require(request.endTime() != null && (request.endTime().equals("24:00") || TIME.matcher(request.endTime()).matches()),
            "endTime must be HH:MM or 24:00");
        require(request.endTime().compareTo(request.startTime()) > 0, "endTime must be after startTime");
        LocalDate today = LocalDate.ofInstant(clock.instant(), LIBRARY_ZONE);
        if ("ONE_TIME".equals(request.kind())) {
            require(request.date() != null, "date is required for one-time unavailability");
            require(!request.date().isBefore(today), "date must be today or later");
            require(request.weekday() == null && request.validFrom() == null && request.validUntil() == null,
                "One-time unavailability takes only date, startTime, and endTime");
            return entries.insert(memberId, "ONE_TIME", request.date(), null, null, null, request.startTime(), request.endTime());
        }
        require("WEEKLY".equals(request.kind()), "kind must be ONE_TIME or WEEKLY");
        require(request.date() == null, "Weekly unavailability does not take a date");
        require(request.weekday() != null && request.weekday() >= 1 && request.weekday() <= 7,
            "weekday must be 1 (Monday) through 7 (Sunday)");
        LocalDate from = request.validFrom() == null ? today : request.validFrom();
        require(request.validUntil() == null || !request.validUntil().isBefore(from), "validUntil must not be before validFrom");
        require(request.validUntil() == null || !request.validUntil().isBefore(today), "validUntil must be today or later");
        return entries.insert(memberId, "WEEKLY", null, request.weekday(), from, request.validUntil(),
            request.startTime(), request.endTime());
    }

    @Transactional
    public void delete(String email, long id) {
        assignments.lockAssignments();
        if (!entries.delete(member(email, true), id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Unavailability not found");
        }
    }

    private long member(String email, boolean lock) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sign in to manage availability");
        }
        return entries.activeMember(email, lock).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.FORBIDDEN, "Active group membership is required"));
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
        }
    }
}
