package com.ttg.devknowledgeplatform.common.repository;

import com.ttg.devknowledgeplatform.common.entity.SysParam;
import com.ttg.devknowledgeplatform.common.enums.ParamKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Persistence operations for {@link SysParam}.
 *
 * <p>The primary access pattern is lookup by {@link ParamKey} name — used by
 * {@link com.ttg.devknowledgeplatform.common.service.SysParamService} to load and upsert
 * cached vectors and thresholds computed by both the {@code ai-service} and {@code api} modules.
 */
@Repository
public interface SysParamRepository extends JpaRepository<SysParam, Integer> {

    /**
     * Finds a parameter row by its {@link ParamKey} identifier.
     *
     * @param name the parameter key
     * @return the parameter if it has been computed and persisted, otherwise empty
     */
    Optional<SysParam> findByName(ParamKey name);
}
