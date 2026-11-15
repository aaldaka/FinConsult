package com.example.notificationservice.event;

import java.util.Map;

// the emails user-service sends today, with the same subjects and text. data holds what the body needs, see
// docs/events.md. A missing value is an invalid event; the message never repeats the link
enum EmailTemplate {

    EMAIL_VERIFICATION("Verify your FinConsult email") {
        @Override
        String body(Map<String, Object> data) {
            return "Click to verify your email: " + text(data, "link")
                    + "\n\nThis link expires in 24 hours.";
        }
    },
    PASSWORD_RESET("Reset your FinConsult password") {
        @Override
        String body(Map<String, Object> data) {
            return "Click to reset your password: " + text(data, "link")
                    + "\n\nThis link expires in " + number(data, "expiresInMinutes") + " minutes. If you didn't request this, ignore this email.";
        }
    };

    private final String subject;

    EmailTemplate(String subject) {
        this.subject = subject;
    }

    String subject() {
        return subject;
    }

    abstract String body(Map<String, Object> data);

    static EmailTemplate find(String name) {
        for (EmailTemplate template : values()) {
            if (template.name().equals(name)) {
                return template;
            }
        }
        return null;
    }

    private static String text(Map<String, Object> data, String field) {
        Object value = data == null ? null : data.get(field);
        if (!(value instanceof String text) || text.isBlank()) {
            throw new InvalidEventException("The email data needs " + field);
        }
        return text;
    }

    private static long number(Map<String, Object> data, String field) {
        Object value = data == null ? null : data.get(field);
        if (!(value instanceof Number number)) {
            throw new InvalidEventException("The email data needs " + field);
        }
        return number.longValue();
    }
}
