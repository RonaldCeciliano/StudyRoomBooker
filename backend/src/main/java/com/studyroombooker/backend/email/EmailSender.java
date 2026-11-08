package com.studyroombooker.backend.email;

/**
 * Application boundary for outgoing email. Services depend on this interface so the
 * provider (logging in development, SMTP in production) can change without touching
 * notification or authentication code.
 */
public interface EmailSender {

	/**
	 * Sends the message or throws {@link EmailDeliveryException} when the provider
	 * rejects it, so callers such as a notification outbox can record the failure.
	 */
	void send(EmailMessage message);
}
