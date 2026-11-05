package com.studyroombooker.backend.controller;

import com.studyroombooker.backend.service.StudyBlockService;
import com.studyroombooker.backend.service.StudyBlockService.CreateRequest;
import com.studyroombooker.backend.service.StudyBlockService.CreatedBlock;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
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
}
