package com.ttg.devknowledgeplatform.repository;

import com.ttg.devknowledgeplatform.common.entity.SysParam;
import com.ttg.devknowledgeplatform.common.enums.ParamKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Persistence operations for {@link SysParam}.
 *
 * <p>The primary access pattern is lookup by {@link ParamKey} name — used by
 * {@code CorpusStatisticsService} to load cached centroid vectors and thresholds
 * at startup and after each scheduled refresh.
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
