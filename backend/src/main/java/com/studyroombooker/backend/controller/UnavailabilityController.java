package com.studyroombooker.backend.controller;

import java.security.Principal;
import java.util.List;

import com.studyroombooker.backend.repository.UnavailabilityRepository.Entry;
import com.studyroombooker.backend.service.UnavailabilityService;
import com.studyroombooker.backend.service.UnavailabilityService.CreateRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Identity always comes from the session principal; there is no member ID in the path. */
@RestController
public class UnavailabilityController {
    private final UnavailabilityService service;

    public UnavailabilityController(UnavailabilityService service) { this.service = service; }

    @GetMapping("/api/me/unavailability")
    public List<Entry> list(Principal principal) {
        return service.list(name(principal));
    }

    @PostMapping("/api/me/unavailability")
    @ResponseStatus(HttpStatus.CREATED)
    public Entry create(@RequestBody CreateRequest request, Principal principal) {
        return service.create(name(principal), request);
    }

    @DeleteMapping("/api/me/unavailability/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id, Principal principal) {
        service.delete(name(principal), id);
    }

    private static String name(Principal principal) {
        return principal == null ? null : principal.getName();
    }
}
