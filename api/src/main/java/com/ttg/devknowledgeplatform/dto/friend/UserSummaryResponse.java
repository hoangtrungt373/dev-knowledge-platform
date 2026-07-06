package com.ttg.devknowledgeplatform.dto.friend;

/**
 * Minimal public-facing view of a user, embedded in search results, friend requests, and
 * friend list entries.
 *
 * @param userUuid       public UUID of the user
 * @param username       display username
 * @param firstName      first name
 * @param lastName       last name
 * @param profilePicture resolved avatar URL (presigned if stored as an object key)
 * @param status         presence status (e.g. {@code ONLINE}, {@code OFFLINE})
 */
public record UserSummaryResponse(
        String userUuid,
        String username,
        String firstName,
        String lastName,
        String profilePicture,
        String status
) {
}
