package com.studyroombooker.backend;

import com.studyroombooker.backend.email.EmailDeliveryException;
import com.studyroombooker.backend.email.EmailMessage;
import com.studyroombooker.backend.email.EmailSender;
import com.studyroombooker.backend.email.LoggingEmailSender;
import com.studyroombooker.backend.email.SmtpEmailSender;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.mail.autoconfigure.MailSenderAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpEmailSenderTests {

    private static final EmailMessage MESSAGE =
        new EmailMessage("member@example.test", "Booking reminder", "Book room 101 on LibCal.");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(MailSenderAutoConfiguration.class))
        .withUserConfiguration(LoggingEmailSender.class, SmtpEmailSender.class);

    @Test
    void sendsPlainTextMailFromTheConfiguredAddress() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        sender(mailSender, "group@example.test").send(MESSAGE);

        var captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(captor.capture());
        SimpleMailMessage sent = captor.getValue();
        assertThat(sent.getFrom()).isEqualTo("group@example.test");
        assertThat(sent.getTo()).containsExactly("member@example.test");
        assertThat(sent.getSubject()).isEqualTo("Booking reminder");
        assertThat(sent.getText()).isEqualTo("Book room 101 on LibCal.");
    }

    @Test
    void wrapsProviderFailures() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        doThrow(new MailSendException("connection refused")).when(mailSender).send(any(SimpleMailMessage.class));

        assertThatThrownBy(() -> sender(mailSender, "group@example.test").send(MESSAGE))
            .isInstanceOf(EmailDeliveryException.class)
            .hasCauseInstanceOf(MailSendException.class);
    }

    @Test
    void requiresCredentialsAndFromAddress() {
        assertThatThrownBy(() -> sender(mock(JavaMailSender.class), " "))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SmtpEmailSender(mock(JavaMailSender.class), "", "fake-app-password", "group@example.test"))
            .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new SmtpEmailSender(mock(JavaMailSender.class), "fake-user@example.test", "", "group@example.test"))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void prodUsesSmtpWithEnvironmentSettings() {
        contextRunner.withPropertyValues(
                "spring.profiles.active=prod",
                "spring.mail.host=smtp.example.test",
                "spring.mail.port=587",
                "spring.mail.username=fake-user@example.test",
                "spring.mail.password=fake-app-password",
                "app.email.from=fake-user@example.test")
            .run(context -> {
                assertThat(context).getBean(EmailSender.class).isInstanceOf(SmtpEmailSender.class);
                var mail = context.getBean(JavaMailSenderImpl.class);
                assertThat(mail.getHost()).isEqualTo("smtp.example.test");
                assertThat(mail.getUsername()).isEqualTo("fake-user@example.test");
            });
    }

    @Test
    void devAndTestNeverRegisterSmtpEvenWithMailSettings() {
        for (String profile : new String[] {"dev", "test"}) {
            contextRunner.withPropertyValues(
                    "spring.profiles.active=" + profile,
                    "spring.mail.host=smtp.example.test",
                    "app.email.from=fake-user@example.test")
                .run(context -> {
                    assertThat(context).getBean(EmailSender.class).isInstanceOf(LoggingEmailSender.class);
                    assertThat(context).doesNotHaveBean(SmtpEmailSender.class);
                });
        }
    }

    @Test
    void prodFailsFastWithoutMailConfiguration() {
        contextRunner.withPropertyValues("spring.profiles.active=prod")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void committedProdConfigUsesGmailStartTlsFromEnvironment() {
        prodConfigRunner()
            .withSystemProperties("SMTP_USERNAME=fake-user@example.test", "SMTP_PASSWORD=fake-app-password")
            .run(context -> {
                assertThat(context).getBean(EmailSender.class).isInstanceOf(SmtpEmailSender.class);
                var mail = context.getBean(JavaMailSenderImpl.class);
                assertThat(mail.getHost()).isEqualTo("smtp.gmail.com");
                assertThat(mail.getPort()).isEqualTo(587);
                assertThat(mail.getUsername()).isEqualTo("fake-user@example.test");
                assertThat(mail.getJavaMailProperties())
                    .containsEntry("mail.smtp.auth", "true")
                    .containsEntry("mail.smtp.starttls.required", "true");
                assertThat(context.getEnvironment().getProperty("app.email.from")).isEqualTo("fake-user@example.test");
                assertThat(context.getEnvironment().getProperty("management.health.mail.enabled")).isEqualTo("false");
            });
    }

    @Test
    void committedProdConfigFailsWithoutCredentials() {
        prodConfigRunner().run(context -> assertThat(context).hasFailed());
    }

    private static SmtpEmailSender sender(JavaMailSender mailSender, String from) {
        return new SmtpEmailSender(mailSender, "fake-user@example.test", "fake-app-password", from);
    }

    private ApplicationContextRunner prodConfigRunner() {
        return contextRunner
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withPropertyValues("spring.profiles.active=prod");
    }
}
