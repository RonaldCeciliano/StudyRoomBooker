package com.studyroombooker.backend.auth;

import java.security.Principal;

import org.springframework.http.HttpStatus;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Reports the current session and issues the CSRF cookie the React app echoes on writes. */
@RestController
public class SessionController {
    private final LoginTokenRepository members;

    public SessionController(LoginTokenRepository members) { this.members = members; }

    public record SessionResponse(boolean authenticated, String email, String name) {}

    @GetMapping("/api/session")
    public SessionResponse session(Principal principal, CsrfToken csrfToken) {
        csrfToken.getToken(); // Loading the deferred token writes the XSRF-TOKEN cookie.
        if (principal == null) {
            return new SessionResponse(false, null, null);
        }
        return members.activeMember(principal.getName())
            .map(member -> new SessionResponse(true, member.email(), member.name()))
            .orElseGet(() -> new SessionResponse(false, null, null));
    }

    /**
     * Reached only when {@code POST /ott/generate} has no email (the security filter handles
     * every other request), so a blank form gets the same 204 as any other address.
     */
    @PostMapping("/ott/generate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void blankSignInRequest() {
    }
}
