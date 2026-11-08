package com.studyroombooker.backend.email;

import java.util.Objects;

/** A plain-text email addressed to a single recipient. */
public record EmailMessage(String to, String subject, String body) {

	public EmailMessage {
		requireText(to, "to");
		requireText(subject, "subject");
		Objects.requireNonNull(body, "body");
	}

	private static void requireText(String value, String name) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
	}
}
