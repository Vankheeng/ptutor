package com.ptutor.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.ptutor.backend.exception.ApiException;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String SUBJECT = "PTutor password reset OTP";

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    public void sendOtp(String recipientEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(fromAddress);
        message.setTo(recipientEmail);
        message.setSubject(SUBJECT);
        message.setText("Your PTutor password reset OTP is " + otp
                + ". It expires in 5 minutes. Do not share this code with anyone.");

        try {
            mailSender.send(message);
        } catch (MailException exception) {
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "EMAIL_DELIVERY_FAILED",
                    "Unable to send password reset email");
        }
    }
}
