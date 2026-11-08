package com.studyroombooker.backend.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.HashSet;
import java.util.List;

import com.studyroombooker.backend.repository.StudyBlockRepository;
import com.studyroombooker.backend.repository.AssignmentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StudyBlockService {
    private static final ZoneId LIBRARY_ZONE = ZoneId.of("America/Chicago");
    private final StudyBlockRepository blocks;
    private final Clock clock;
    private final AssignmentRepository assignmentRepository;
    private final AssignmentService assignmentService;

    public StudyBlockService(StudyBlockRepository blocks, Clock clock,
                             AssignmentRepository assignmentRepository, AssignmentService assignmentService) {
        this.blocks = blocks;
        this.clock = clock;
        this.assignmentRepository = assignmentRepository;
        this.assignmentService = assignmentService;
    }

    public record CreateRequest(LocalDate date, LocalTime startTime, LocalTime endTime,
                                Long preferredRoomId, List<Long> backupRoomIds) {}
    public record CreatedBlock(long id, String kind, LocalDate date, LocalTime startTime,
                               LocalTime endTime, long preferredRoomId, List<Long> backupRoomIds) {}

    @Transactional
    public CreatedBlock create(CreateRequest request) {
        List<Long> backups = validate(request);
        long id = blocks.insert(request.date(), request.startTime(), request.endTime(), request.preferredRoomId(), backups);
        generateShifts(id);
        return new CreatedBlock(id, "ONE_TIME", request.date(), request.startTime(), request.endTime(),
            request.preferredRoomId(), List.copyOf(backups));
    }

    /**
     * Edits a block that has not started. Changing the date or times cancels BOOKED shifts
     * (flagged {@code libcal_cancel_required}) and regenerates and reassigns the rest;
     * changing only rooms re-plans unbooked shifts and keeps existing reservations.
     */
    @Transactional
    public CreatedBlock update(long id, CreateRequest request) {
        assignmentRepository.lockAssignments();
        var existing = blocks.findOneTime(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "One-time study block not found"));
        if (!instant(existing.date(), existing.startTime()).isAfter(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Study blocks that have started cannot be edited");
        }
        List<Long> backups = validate(request);
        blocks.update(id, request.date(), request.startTime(), request.endTime(), request.preferredRoomId(), backups);
        boolean timesChanged = !existing.date().equals(request.date())
            || !existing.startTime().equals(request.startTime()) || !existing.endTime().equals(request.endTime());
        if (timesChanged) {
            blocks.clearShiftsForRegeneration(id);
        }
        else {
            blocks.replanRoom(id, request.preferredRoomId());
        }
        generateShifts(id);
        return new CreatedBlock(id, "ONE_TIME", request.date(), request.startTime(), request.endTime(),
            request.preferredRoomId(), List.copyOf(backups));
    }

    @Transactional(readOnly = true)
    public List<StudyBlockRepository.BlockView> upcoming() {
        return blocks.upcoming(LocalDate.ofInstant(clock.instant(), LIBRARY_ZONE));
    }

    @Transactional(readOnly = true)
    public List<StudyBlockRepository.Room> rooms() {
        return blocks.activeRooms();
    }

    private List<Long> validate(CreateRequest request) {
        require(request.date() != null && request.startTime() != null && request.endTime() != null
            && request.preferredRoomId() != null, "date, startTime, endTime, and preferredRoomId are required");
        var start = request.startTime();
        var end = request.endTime();
        require(end.isAfter(start), "endTime must be after startTime on the same date");
        require(aligned(start) && aligned(end), "Times must align to 15-minute boundaries");
        Instant startsAt = instant(request.date(), start);
        instant(request.date(), end);
        require(startsAt.isAfter(clock.instant()), "Study blocks must start in the future");
        var hours = blocks.hours(request.date().getDayOfWeek().getValue())
            .orElseThrow(() -> badRequest("Library hours are unavailable for this weekday"));
        require(start.toSecondOfDay() >= hours.opensAtSeconds()
            && end.toSecondOfDay() <= hours.closesAtSeconds(), "Study block must fit within library hours");
        List<Long> backups = request.backupRoomIds() == null ? List.of() : request.backupRoomIds();
        require(backups.stream().noneMatch(java.util.Objects::isNull), "Backup room IDs must not be null");
        require(new HashSet<>(backups).size() == backups.size(), "Backup rooms must be unique");
        require(!backups.contains(request.preferredRoomId()), "Preferred room cannot also be a backup");
        var rooms = new java.util.ArrayList<Long>(backups);
        rooms.add(request.preferredRoomId());
        // Lock in stable order so concurrent creates with reversed backup order do not deadlock.
        for (long room : rooms.stream().sorted().toList()) {
            require(blocks.activeRoom(room), "All rooms must exist and be active");
        }
        return backups;
    }

    /** Repeated generation preserves existing assignments and booking state. */
    @Transactional
    public void generateShifts(long blockId) {
        // Acquire the shared assignment lock before block/shift locks on every entry path.
        assignmentRepository.lockAssignments();
        var block = blocks.findOneTime(blockId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "One-time study block not found"));
        blocks.insertShifts(block, ShiftSplitter.split(instant(block.date(), block.startTime()),
            instant(block.date(), block.endTime())));
        assignmentService.assignBlock(blockId);
    }

    private static boolean aligned(LocalTime time) {
        return time.getNano() == 0 && time.toSecondOfDay() % 900 == 0;
    }

    private static Instant instant(LocalDate date, LocalTime time) {
        var local = LocalDateTime.of(date, time);
        var offsets = LIBRARY_ZONE.getRules().getValidOffsets(local);
        require(offsets.size() == 1, "Times must exist and be unambiguous in America/Chicago");
        return local.toInstant(offsets.getFirst());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw badRequest(message);
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
