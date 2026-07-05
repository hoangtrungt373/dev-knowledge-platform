package com.ttg.devknowledgeplatform.service.impl;

import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ttg.devknowledgeplatform.service.EmailService;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Throwable.class)
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    @Value("${app.mail.from}")
    private String fromAddress;

    @Value("${app.mail.otp.expiration-minutes}")
    private int otpExpirationMinutes;

    @Override
    public void sendOtpEmail(String to, String otp) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, "UTF-8");
            helper.setFrom(fromAddress);
            helper.setTo(to);
            helper.setSubject("Your verification code");
            helper.setText(buildOtpHtml(otp), true);

            mailSender.send(message);
            log.info("OTP email sent to: {}", to);
        } catch (MessagingException | MailException e) {
            log.error("Failed to send OTP email to {}: {}", to, e.getMessage());
            throw new org.springframework.mail.MailSendException("Failed to send OTP email", e);
        }
    }

    private String buildOtpHtml(String otp) {
        String digits = otp.chars()
                .mapToObj(c -> "<span style=\"display:inline-block;width:44px;height:52px;line-height:52px;" +
                        "text-align:center;font-size:28px;font-weight:700;color:#1a1a2e;" +
                        "background:#f0f4ff;border:2px solid #d0d7ff;border-radius:8px;" +
                        "margin:0 4px;\">" + (char) c + "</span>")
                .collect(Collectors.joining());

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1"></head>
                <body style="margin:0;padding:0;background:#f6f8fa;font-family:Inter,Segoe UI,Arial,sans-serif;">
                  <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f6f8fa;padding:40px 0;">
                    <tr><td align="center">
                      <table width="480" cellpadding="0" cellspacing="0"
                             style="background:#ffffff;border-radius:12px;box-shadow:0 2px 12px rgba(0,0,0,.08);overflow:hidden;">

                        <!-- Header -->
                        <tr>
                          <td style="background:#4f46e5;padding:32px 40px;text-align:center;">
                            <p style="margin:0;font-size:22px;font-weight:700;color:#ffffff;letter-spacing:.5px;">
                              Dev Knowledge Platform
                            </p>
                          </td>
                        </tr>

                        <!-- Body -->
                        <tr>
                          <td style="padding:40px;">
                            <p style="margin:0 0 8px;font-size:22px;font-weight:700;color:#1a1a2e;">
                              Verify your email address
                            </p>
                            <p style="margin:0 0 28px;font-size:15px;color:#6b7280;line-height:1.6;">
                              Use the code below to complete your registration.
                              It expires in <strong>%d minutes</strong>.
                            </p>

                            <!-- OTP digits -->
                            <div style="text-align:center;margin:0 0 28px;">
                              %s
                            </div>

                            <p style="margin:0;font-size:13px;color:#9ca3af;line-height:1.6;">
                              If you didn't create an account, you can safely ignore this email.
                              Never share this code with anyone.
                            </p>
                          </td>
                        </tr>

                        <!-- Footer -->
                        <tr>
                          <td style="background:#f9fafb;padding:20px 40px;border-top:1px solid #e5e7eb;text-align:center;">
                            <p style="margin:0;font-size:12px;color:#9ca3af;">
                              &copy; 2025 Dev Knowledge Platform. All rights reserved.
                            </p>
                          </td>
                        </tr>

                      </table>
                    </td></tr>
                  </table>
                </body>
                </html>
                """.formatted(otpExpirationMinutes, digits);
    }
}
