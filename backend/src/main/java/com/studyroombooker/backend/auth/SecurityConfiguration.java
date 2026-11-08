package com.studyroombooker.backend.auth;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;

/**
 * Session-based security for the same-origin React app.
 *
 * <ul>
 * <li>{@code POST /ott/generate} (form field {@code username}) emails a magic link; always 204.</li>
 * <li>{@code POST /login/ott} (form field {@code token}) consumes it and starts a session; 204 or 401.</li>
 * <li>{@code POST /logout} ends the session; 204.</li>
 * <li>Every {@code /api/**} route except {@code GET /api/session} requires a member session.</li>
 * <li>Sessions of members deactivated after sign-in end on their next request.</li>
 * <li>CSRF uses the SPA pattern: the {@code XSRF-TOKEN} cookie is echoed in {@code X-XSRF-TOKEN}.</li>
 * </ul>
 * No GET request consumes a token: the backend serves no GET handler for sign-in at all.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, MagicLinkTokenService tokenService,
            MagicLinkEmailHandler emailHandler, LoginTokenRepository members) throws Exception {
        http
            .addFilterBefore(new ActiveMemberFilter(members), AnonymousAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/session").permitAll()
                // Email action links authorize with their own single-use token.
                .requestMatchers("/api/actions/**").permitAll()
                // Blank emails skip the token filter; SessionController answers them with the same 204.
                .requestMatchers(HttpMethod.POST, "/ott/generate").permitAll()
                .requestMatchers("/api/**").authenticated()
                .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**",
                    "/openapi/**", "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**").permitAll()
                .requestMatchers("/error").permitAll()
                .anyRequest().denyAll())
            .csrf(csrf -> csrf.spa())
            .requestCache(cache -> cache.disable())
            .oneTimeTokenLogin(ott -> ott
                .tokenService(tokenService)
                .tokenGenerationSuccessHandler(emailHandler)
                .showDefaultSubmitPage(false)
                .loginPage("/login")
                .loginProcessingUrl("/login/ott")
                .authenticationSuccessHandler((request, response, authentication) ->
                    response.setStatus(HttpStatus.NO_CONTENT.value()))
                .authenticationFailureHandler((request, response, exception) ->
                    ProblemResponses.write(response, 401, "Unauthorized",
                        "This sign-in link is invalid, expired, or already used.")))
            .logout(logout -> logout
                .logoutUrl("/logout")
                .deleteCookies("JSESSIONID")
                .logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT)))
            .exceptionHandling(errors -> errors
                .authenticationEntryPoint((request, response, exception) ->
                    ProblemResponses.write(response, 401, "Unauthorized", "Sign in to continue."))
                .accessDeniedHandler((request, response, exception) ->
                    ProblemResponses.write(response, 403, "Forbidden", "The request was not allowed.")));
        return http.build();
    }
}
