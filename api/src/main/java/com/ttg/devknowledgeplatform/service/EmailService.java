package com.ttg.devknowledgeplatform.service;

/**
 * Sends transactional emails on behalf of the platform.
 *
 * <p>The current implementation delivers HTML emails via JavaMailSender (SMTP).
 * All methods are fire-and-forget from the caller's perspective; failures are
 * logged and re-thrown as {@link org.springframework.mail.MailSendException}.
 */
public interface EmailService {

    /**
     * Sends an HTML email containing a one-time password to the given address.
     *
     * @param to  the recipient's email address
     * @param otp the OTP digits to embed in the email body
     * @throws org.springframework.mail.MailSendException if delivery fails
     */
    void sendOtpEmail(String to, String otp);
}
