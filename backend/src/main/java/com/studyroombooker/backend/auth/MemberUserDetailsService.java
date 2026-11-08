package com.studyroombooker.backend.auth;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Resolves only active members. The principal name is the member's primary email, which
 * services such as shift release use as the trusted identity. There are no passwords.
 */
@Service
public class MemberUserDetailsService implements UserDetailsService {
    private final LoginTokenRepository members;

    public MemberUserDetailsService(LoginTokenRepository members) { this.members = members; }

    @Override
    public UserDetails loadUserByUsername(String email) {
        var member = members.activeMember(email).orElseThrow(() -> new UsernameNotFoundException("Not an active member"));
        return User.withUsername(member.email()).password("").authorities("ROLE_MEMBER").build();
    }
}
