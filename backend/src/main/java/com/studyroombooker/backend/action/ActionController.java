package com.studyroombooker.backend.action;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Public (no session): the single-use token is the authorization. Writes still need CSRF. */
@RestController
public class ActionController {
    private final ActionTokenService actions;

    public ActionController(ActionTokenService actions) { this.actions = actions; }

    public record TokenRequest(String token) {}

    @GetMapping("/api/actions/booked")
    public ActionTokenService.Preview preview(@RequestParam(required = false) String token) {
        return actions.preview(token);
    }

    @PostMapping("/api/actions/booked")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markBooked(@RequestBody(required = false) TokenRequest request) {
        actions.markBooked(request == null ? null : request.token());
    }
}
