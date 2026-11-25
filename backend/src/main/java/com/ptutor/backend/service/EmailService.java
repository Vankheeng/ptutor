package com.ptutor.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.ptutor.backend.exception.ApiException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private static final String SUBJECT = "Mã OTP đặt lại mật khẩu PTutor";

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${spring.mail.username:}")
    private String smtpUsername;

    public void sendOtp(String recipientEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(resolveFromAddress());
        message.setTo(recipientEmail);
        message.setSubject(SUBJECT);
        message.setText("Mã OTP đặt lại mật khẩu PTutor của bạn là " + otp
                + ". Mã có hiệu lực trong 5 phút. Vui lòng không chia sẻ mã này với bất kỳ ai.");

        try {
            mailSender.send(message);
        } catch (MailException exception) {
            log.error("Không gửi được email đặt lại mật khẩu. Nguyên nhân SMTP: {}",
                    exception.getMostSpecificCause().getMessage());
            throw new ApiException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "EMAIL_DELIVERY_FAILED",
                    "Không thể gửi email đặt lại mật khẩu. Vui lòng thử lại sau.");
        }
    }

    public void sendWithdrawalOtp(String recipientEmail, String otp, String amount, String maskedAccount) {
        sendSecurityOtp(recipientEmail, "Mã xác minh rút tiền PTutor",
                "Mã xác minh yêu cầu rút " + amount + " VND đến tài khoản ngân hàng " + maskedAccount
                        + " là " + otp + ". Mã có hiệu lực trong 5 phút. Vui lòng không chia sẻ mã này với bất kỳ ai.");
    }

    public void sendBankAccountUpdateOtp(String recipientEmail, String otp, String bankName, String maskedAccount) {
        sendSecurityOtp(recipientEmail, "Mã xác minh thay đổi tài khoản ngân hàng PTutor",
                "Mã xác minh để đặt tài khoản " + bankName + " " + maskedAccount
                        + " làm tài khoản nhận tiền là " + otp
                        + ". Mã có hiệu lực trong 5 phút. Vui lòng không chia sẻ mã này với bất kỳ ai.");
    }

    public void sendAccountSuspended(
            String recipientEmail, String reason, String type, String suspendedUntil) {
        String duration = "TEMPORARY".equals(type)
                ? " đến " + suspendedUntil
                : " vô thời hạn";
        send(recipientEmail, "Tài khoản PTutor bị tạm khóa",
                "Tài khoản PTutor của bạn đã bị khóa" + duration + ".\nLý do: " + reason);
    }

    public void sendAccountReactivated(String recipientEmail) {
        send(recipientEmail, "Tài khoản PTutor đã được mở lại",
                "Tài khoản PTutor của bạn đã được mở lại. Bạn có thể đăng nhập.");
    }

    private void send(String recipientEmail, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(resolveFromAddress());
        message.setTo(recipientEmail);
        message.setSubject(subject);
        message.setText(content);
        mailSender.send(message);
    }

    private void sendSecurityOtp(String recipientEmail, String subject, String content) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(resolveFromAddress());
        message.setTo(recipientEmail);
        message.setSubject(subject);
        message.setText(content);
        try {
            mailSender.send(message);
        } catch (MailException exception) {
            log.error("Không gửi được email xác minh. Nguyên nhân SMTP: {}",
                    exception.getMostSpecificCause().getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_DELIVERY_FAILED",
                    "Không thể gửi email xác minh. Vui lòng thử lại sau.");
        }
    }

    private String resolveFromAddress() {
        if (StringUtils.hasText(fromAddress)) {
            return fromAddress.strip();
        }
        if (StringUtils.hasText(smtpUsername)) {
            return smtpUsername.strip();
        }
        throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "EMAIL_NOT_CONFIGURED",
                "Chưa cấu hình địa chỉ email gửi đi.");
    }
}
