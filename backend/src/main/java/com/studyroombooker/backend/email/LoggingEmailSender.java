package com.studyroombooker.backend.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Development and test sender: writes each message to the application log instead of
 * contacting a mail server. Active for every profile except {@code prod}, so local runs
 * and tests can never send real mail.
 */
@Component
@Profile("!prod")
public class LoggingEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

	@Override
	public void send(EmailMessage message) {
		log.info("Email not sent (logging sender)\nTo: {}\nSubject: {}\n\n{}",
				message.to(), message.subject(), message.body());
	}
}
