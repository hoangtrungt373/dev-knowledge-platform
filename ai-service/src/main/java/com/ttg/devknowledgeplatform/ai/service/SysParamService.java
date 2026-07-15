package com.ttg.devknowledgeplatform.ai.service;

import com.ttg.devknowledgeplatform.ai.entity.SysParam;
import com.ttg.devknowledgeplatform.ai.enums.ParamKey;

import java.util.Optional;

/**
 * Generic get/upsert access to {@link SysParam} rows, keyed by {@link ParamKey}.
 *
 * <p>Deliberately string-in/string-out — this service knows nothing about vector notation,
 * JSON, or any other value encoding. Its two callers, corpus centroid caching
 * ({@code CorpusStatisticsServiceImpl}) and prompt-injection prototype embedding caching
 * ({@code PromptGuardStage}), each own their own serialization format and interpret the raw
 * {@link SysParam#getValue()} string themselves. This keeps the service reusable across both
 * without coupling it to either one's value format.
 */
public interface SysParamService {

    /**
     * Looks up the raw stored value for a parameter key.
     *
     * @param key the parameter key
     * @return the raw value if the parameter has been computed and persisted, otherwise empty
     */
    Optional<String> getValue(ParamKey key);

    /**
     * Inserts or updates the row for {@code key} with {@code value}, stamping
     * {@link SysParam#getComputedAt()} with the current time.
     *
     * @param key   the parameter key
     * @param value the raw value to persist
     */
    void upsert(ParamKey key, String value);
}
