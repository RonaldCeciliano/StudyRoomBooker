package com.studyroombooker.backend.email;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Production sender: delivers plain-text mail over SMTP (Gmail with an app password).
 * Connection settings and credentials come only from environment variables; see the
 * backend README. Active only for the {@code prod} profile.
 */
@Component
@Profile("prod")
public class SmtpEmailSender implements EmailSender {

	private final JavaMailSender mailSender;
	private final String from;

	public SmtpEmailSender(JavaMailSender mailSender,
			@Value("${spring.mail.username:}") String username,
			@Value("${spring.mail.password:}") String password,
			@Value("${app.email.from:}") String from) {
		requireConfigured(username, "spring.mail.username (SMTP_USERNAME)");
		requireConfigured(password, "spring.mail.password (SMTP_PASSWORD)");
		requireConfigured(from, "app.email.from (EMAIL_FROM)");
		this.mailSender = mailSender;
		this.from = from;
	}

	/** Fails startup instead of discovering missing credentials on the first send. */
	private static void requireConfigured(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(name + " must be configured for the prod profile");
		}
	}

	@Override
	public void send(EmailMessage message) {
		var mail = new SimpleMailMessage();
		mail.setFrom(from);
		mail.setTo(message.to());
		mail.setSubject(message.subject());
		mail.setText(message.body());
		try {
			mailSender.send(mail);
		}
		catch (MailException ex) {
			throw new EmailDeliveryException("SMTP delivery failed", ex);
		}
	}
}
