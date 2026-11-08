package com.studyroombooker.backend.auth;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

/** Writes fixed ProblemDetail bodies from security filters, before Spring MVC runs. */
final class ProblemResponses {
    private ProblemResponses() {}

    static void write(HttpServletResponse response, int status, String title, String detail) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"status\":" + status
            + ",\"detail\":\"" + detail + "\"}");
    }
}
