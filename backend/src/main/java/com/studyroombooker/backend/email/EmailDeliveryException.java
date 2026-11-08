package com.studyroombooker.backend.email;

/** Raised when an {@link EmailSender} cannot deliver a message. */
public class EmailDeliveryException extends RuntimeException {

	public EmailDeliveryException(String message, Throwable cause) {
		super(message, cause);
	}
}
