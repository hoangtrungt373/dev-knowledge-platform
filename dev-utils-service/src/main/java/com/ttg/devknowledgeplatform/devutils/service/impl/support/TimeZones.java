package com.ttg.devknowledgeplatform.devutils.service.impl.support;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZoneOffset;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devutils.exception.DevUtilsErrorCode;

/**
 * Resolves an optional IANA zone id string into a real {@link ZoneId} — shared by
 * {@code DateTimeToUnixOperation}/{@code UnixToDateTimeOperation}, both of which need the exact
 * same "blank means UTC, otherwise a real zone id or a rejected input" resolution, so this was
 * extracted the moment the second call site landed rather than duplicated (this module's own
 * support classes — see {@code TextScanning}/{@code ParserLocations}'s own Javadoc — are only
 * ever pulled out once real duplication across 2+ operations actually exists, not pre-emptively
 * for a single new operation). {@code public}, not package-private — unlike
 * {@code TextScanning}/{@code ParserLocations} (used only by other support classes within this
 * same package), this one is called directly by operation classes living one package up, the
 * same visibility {@code CurlyBraceFormatter}/{@code SqlFormatter} already have for the same
 * reason.
 *
 * <p>This module has no persisted user/timezone context of any kind (a fully public, stateless
 * endpoint — see root {@code CLAUDE.md}'s Security section), so there is no "the caller's own
 * timezone" to default to server-side; the GUI (which knows the browser's own detected zone via
 * the {@code Intl} API) is expected to supply {@code zoneId} explicitly, and this only supplies a
 * deterministic fallback (UTC) for a caller with no preference at all.
 */
public final class TimeZones {

    private TimeZones() {
    }

    /**
     * @param zoneId a real IANA zone id (e.g. {@code "Asia/Ho_Chi_Minh"}), or {@code null}/blank
     *               to default to UTC.
     * @return the resolved zone.
     * @throws BusinessException wrapping {@link DevUtilsErrorCode#INVALID_TIME_ZONE} when
     *                           {@code zoneId} is non-blank but not a real zone id.
     */
    public static ZoneId parse(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return ZoneOffset.UTC;
        }
        try {
            return ZoneId.of(zoneId.trim());
        } catch (DateTimeException e) {
            throw new BusinessException(DevUtilsErrorCode.INVALID_TIME_ZONE, (Object) zoneId);
        }
    }
}
