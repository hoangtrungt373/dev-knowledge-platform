package com.ttg.devknowledgeplatform.social.enums;

/**
 * Presence status of a {@link com.ttg.devknowledgeplatform.social.entity.SocialProfile}, embedded
 * in {@code UserSummaryResponse} for friend/DM-sender display.
 *
 * <p>Deliberately this module's own enum, not a reuse of {@code common.enums.UserStatus} — that
 * type is a field on {@code common.entity.User}, which this module no longer maps at all (see
 * {@link com.ttg.devknowledgeplatform.social.entity.SocialProfile}'s Javadoc). Reusing it would
 * leave a stray dependency on `User`'s value types even after the entity coupling itself was
 * removed.
 */
public enum ProfileStatus {
    ONLINE,
    OFFLINE,
    AWAY,
    BUSY
}
