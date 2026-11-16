package com.example.notificationservice.service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailPreparationException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;

// sends one email with an html body and a plain text body for mail apps that do not show html. It is not async and
// does not catch anything: the email consumer needs the failure so the message is retried and then sent to the DLT
@Service
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromAddress; // the system address

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendEmail(String to, String subject, String text, String html) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, "FinConsult");
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
        } catch (MessagingException | UnsupportedEncodingException e) {
            throw new MailPreparationException("Could not build the email", e);
        }
        mailSender.send(message);
    }
}
