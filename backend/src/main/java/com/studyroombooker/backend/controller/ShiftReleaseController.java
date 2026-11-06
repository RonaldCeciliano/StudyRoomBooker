package com.studyroombooker.backend.controller;

import java.security.Principal;

import com.studyroombooker.backend.service.ShiftReleaseService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ShiftReleaseController {
    private final ShiftReleaseService releases;

    public ShiftReleaseController(ShiftReleaseService releases) { this.releases = releases; }

    @PostMapping("/api/shifts/{id}/release")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void release(@PathVariable long id, Principal principal) {
        releases.release(id, principal == null ? null : principal.getName());
    }
}
