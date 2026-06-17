package com.ttg.devknowledgeplatform.service;

/**
 * Generates, stores, and validates one-time passwords used for email verification.
 *
 * <p>OTPs are keyed by email address and stored in Redis with a configurable TTL.
 * A successful call to {@link #verify} atomically validates and deletes the OTP,
 * preventing replay attacks.
 */
public interface OtpService {

    /**
     * Generates a new numeric OTP, stores it in Redis, and returns it so the caller
     * can forward it to the user (e.g. via email).
     *
     * <p>Any previously stored OTP for the same email is silently overwritten.
     *
     * @param email the email address the OTP is issued for
     * @return the generated OTP string (zero-padded to the configured digit length)
     */
    String generateAndStore(String email);

    /**
     * Validates the supplied OTP against the stored value and deletes it on success.
     *
     * @param email the email address the OTP was issued for
     * @param otp   the OTP value submitted by the user
     * @return {@code true} if the OTP matched and has been consumed; {@code false} if
     *         it was absent, expired, or did not match
     */
    boolean verify(String email, String otp);

    /**
     * Returns {@code true} if an unexpired OTP is currently stored for the given email.
     *
     * <p>Useful for rate-limiting re-send requests without exposing the OTP value.
     *
     * @param email the email address to check
     * @return {@code true} if a pending OTP exists
     */
    boolean hasPendingOtp(String email);

    /**
     * Removes any stored OTP for the given email without validating it.
     *
     * <p>Called to clean up after a registration is abandoned or superseded.
     *
     * @param email the email address whose OTP should be removed
     */
    void delete(String email);
}
