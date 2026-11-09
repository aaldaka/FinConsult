package com.example.userservice.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Sends transactional emails (verification links, password resets) asynchronously
 * so callers aren't blocked on SMTP.
 */
@Slf4j
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress; //sys address

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /**
     * Sends a plain-text email async; failures are logged, not thrown, so a mail
     * outage never breaks the caller.
     *
     * @param type short purpose label for the log line (e.g. "verification", "password-reset"), never the subject or body
     */
    @Async("mailExecutor")
    public void sendEmail(String to, String subject, String body, String type) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromAddress);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Email sent: type={}", type);
        } catch (RuntimeException e) {
            log.error("Failed to send email to {} (type={})", to, type, e);
        }
    }
}
