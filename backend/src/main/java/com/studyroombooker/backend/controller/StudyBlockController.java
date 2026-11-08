package com.studyroombooker.backend.controller;

import java.util.List;

import com.studyroombooker.backend.repository.StudyBlockRepository.BlockView;
import com.studyroombooker.backend.repository.StudyBlockRepository.Room;
import com.studyroombooker.backend.service.StudyBlockService;
import com.studyroombooker.backend.service.StudyBlockService.CreateRequest;
import com.studyroombooker.backend.service.StudyBlockService.CreatedBlock;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StudyBlockController {
    private final StudyBlockService blocks;

    public StudyBlockController(StudyBlockService blocks) { this.blocks = blocks; }

    @PostMapping("/api/study-blocks")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatedBlock create(@RequestBody CreateRequest request) {
        return blocks.create(request);
    }

    @GetMapping("/api/study-blocks")
    public List<CreatedBlock> upcoming() {
        return blocks.upcoming().stream().map(StudyBlockController::view).toList();
    }

    @PutMapping("/api/study-blocks/{id}")
    public CreatedBlock update(@PathVariable long id, @RequestBody CreateRequest request) {
        return blocks.update(id, request);
    }

    @GetMapping("/api/rooms")
    public List<Room> rooms() {
        return blocks.rooms();
    }

    private static CreatedBlock view(BlockView block) {
        return new CreatedBlock(block.id(), "ONE_TIME", block.date(), block.startTime(), block.endTime(),
            block.preferredRoomId(), block.backupRoomIds());
    }
}
