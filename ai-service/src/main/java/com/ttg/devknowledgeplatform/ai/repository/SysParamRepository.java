package com.ttg.devknowledgeplatform.ai.repository;

import com.ttg.devknowledgeplatform.ai.entity.SysParam;
import com.ttg.devknowledgeplatform.ai.enums.ParamKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Persistence operations for {@link SysParam}.
 *
 * <p>The primary access pattern is lookup by {@link ParamKey} name — used by
 * {@link com.ttg.devknowledgeplatform.ai.service.SysParamService} to load and upsert the
 * corpus centroid caches ({@code CorpusStatisticsServiceImpl}) and the prompt-injection
 * prototype embedding cache ({@code PromptGuardStage}).
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
