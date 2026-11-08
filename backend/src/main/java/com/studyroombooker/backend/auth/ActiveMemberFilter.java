package com.studyroombooker.backend.auth;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ends the session of a member who was deactivated after signing in, so removal from the
 * group takes effect on the next request rather than when the session expires.
 */
class ActiveMemberFilter extends OncePerRequestFilter {
    private final LoginTokenRepository members;

    ActiveMemberFilter(LoginTokenRepository members) { this.members = members; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)
                && members.activeMember(authentication.getName()).isEmpty()) {
            var session = request.getSession(false);
            if (session != null) {
                session.invalidate();
            }
            SecurityContextHolder.clearContext();
        }
        chain.doFilter(request, response);
    }
}
