package com.studyroombooker.backend;

import com.studyroombooker.backend.email.EmailMessage;
import com.studyroombooker.backend.email.EmailSender;
import com.studyroombooker.backend.email.LoggingEmailSender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class LoggingEmailSenderTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(LoggingEmailSender.class);

    @Test
    void logsTheMessageInsteadOfSendingIt(CapturedOutput output) {
        new LoggingEmailSender().send(new EmailMessage(
            "member@example.test", "Booking reminder", "Book room 101 on LibCal."));

        assertThat(output)
            .contains("Email not sent (logging sender)")
            .contains("To: member@example.test")
            .contains("Subject: Booking reminder")
            .contains("Book room 101 on LibCal.");
    }

    @Test
    void isTheInjectableSenderForDevAndTestProfiles() {
        for (String profile : new String[] {"dev", "test"}) {
            contextRunner.withPropertyValues("spring.profiles.active=" + profile).run(context ->
                assertThat(context).getBean(EmailSender.class).isInstanceOf(LoggingEmailSender.class));
        }
    }

    @Test
    void isNotRegisteredForTheProductionProfile() {
        contextRunner.withPropertyValues("spring.profiles.active=prod").run(context ->
            assertThat(context).doesNotHaveBean(EmailSender.class));
    }

    @Test
    void rejectsMessagesWithoutRecipientOrSubject() {
        assertThatThrownBy(() -> new EmailMessage(" ", "Subject", "Body"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new EmailMessage("member@example.test", "", "Body"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
