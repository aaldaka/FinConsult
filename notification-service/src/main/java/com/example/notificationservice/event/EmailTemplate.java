package com.example.notificationservice.event;

import org.springframework.web.util.HtmlUtils;

import java.util.Map;

// the emails user-service asks for, with the same subjects as before. Each one has a plain text body (shown by mail
// apps that do not show html) and an html body in the app's colours. data holds what the body needs, see
// docs/events.md. A missing value is an invalid event; the message never repeats the link
enum EmailTemplate {

    EMAIL_VERIFICATION("Verify your FinConsult email") {
        @Override
        String text(Map<String, Object> data) {
            return "Click to verify your email: " + link(data)
                    + "\n\nThis link expires in 24 hours.";
        }

        @Override
        String html(Map<String, Object> data) {
            return EmailLayout.page(new EmailLayout.Content(
                    subject(),
                    "&#9993;&#65039;",
                    "Confirm your email",
                    "Welcome to FinConsult!",
                    "Thanks for signing up. Please confirm your email address so you can log in and book your first consultation.",
                    "Confirm email address",
                    link(data),
                    "This link expires in 24 hours. If you did not create an account, you can ignore this email."));
        }
    },
    PASSWORD_RESET("Reset your FinConsult password") {
        @Override
        String text(Map<String, Object> data) {
            return "Click to reset your password: " + link(data)
                    + "\n\nThis link expires in " + minutes(data) + " minutes. If you didn't request this, ignore this email.";
        }

        @Override
        String html(Map<String, Object> data) {
            return EmailLayout.page(new EmailLayout.Content(
                    subject(),
                    "&#128273;",
                    "Reset your password",
                    "Forgot your password?",
                    "No problem. Click the button below to choose a new one.",
                    "Reset password",
                    link(data),
                    "This link expires in " + minutes(data) + " minutes. If you did not ask for a reset, ignore this email: "
                            + "your password stays the same."));
        }
    };

    private final String subject;

    EmailTemplate(String subject) {
        this.subject = subject;
    }

    String subject() {
        return subject;
    }

    abstract String text(Map<String, Object> data);

    abstract String html(Map<String, Object> data);

    static EmailTemplate find(String name) {
        for (EmailTemplate template : values()) {
            if (template.name().equals(name)) {
                return template;
            }
        }
        return null;
    }

    private static String link(Map<String, Object> data) {
        Object value = data == null ? null : data.get("link");
        if (!(value instanceof String text) || text.isBlank()) {
            throw new InvalidEventException("The email data needs link");
        }
        return text;
    }

    private static long minutes(Map<String, Object> data) {
        Object value = data == null ? null : data.get("expiresInMinutes");
        if (!(value instanceof Number number)) {
            throw new InvalidEventException("The email data needs expiresInMinutes");
        }
        return number.longValue();
    }

    // escapes text and attribute values put into the html
    static String escape(String value) {
        return HtmlUtils.htmlEscape(value);
    }
}
