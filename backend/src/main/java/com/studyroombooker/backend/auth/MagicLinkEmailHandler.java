package com.studyroombooker.backend.auth;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executor;

import com.studyroombooker.backend.email.EmailMessage;
import com.studyroombooker.backend.email.EmailSender;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.security.authentication.ott.OneTimeToken;
import org.springframework.security.web.authentication.ott.OneTimeTokenGenerationSuccessHandler;
import org.springframework.stereotype.Component;

/**
 * Emails the magic link and always answers 204, whether or not the address belongs to a
 * member. Sending happens off the request thread so response timing does not reveal
 * membership either. The link opens the React confirmation page; it never signs in on GET.
 */
@Component
public class MagicLinkEmailHandler implements OneTimeTokenGenerationSuccessHandler {
    private static final Logger log = LoggerFactory.getLogger(MagicLinkEmailHandler.class);

    private final EmailSender emailSender;
    private final MagicLinkTokenService tokens;
    private final String baseUrl;
    private final Executor executor;

    public MagicLinkEmailHandler(EmailSender emailSender, MagicLinkTokenService tokens,
            @Value("${app.base-url:}") String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("app.base-url (APP_BASE_URL) must be configured");
        }
        this.emailSender = emailSender;
        this.tokens = tokens;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        var async = new SimpleAsyncTaskExecutor("magic-link-");
        async.setVirtualThreads(true);
        this.executor = async;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, OneTimeToken token) throws IOException {
        if (token instanceof MagicLinkTokenService.IssuedToken issued && issued.deliverable()) {
            EmailMessage message = message(issued);
            executor.execute(() -> {
                try {
                    emailSender.send(message);
                }
                catch (RuntimeException ex) {
                    log.warn("Magic-link email delivery failed", ex);
                }
            });
        }
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private EmailMessage message(MagicLinkTokenService.IssuedToken token) {
        String link = baseUrl + "/login/magic?token=" + URLEncoder.encode(token.getTokenValue(), StandardCharsets.UTF_8);
        long minutes = tokens.ttl().toMinutes();
        return new EmailMessage(token.getUsername(), "Your StudyRoomBooker sign-in link", """
            Open this link to sign in to StudyRoomBooker, then press Continue:

            %s

            The link works once and expires in %d minutes.
            If you did not ask to sign in, you can ignore this email.
            """.formatted(link, minutes));
    }
}
