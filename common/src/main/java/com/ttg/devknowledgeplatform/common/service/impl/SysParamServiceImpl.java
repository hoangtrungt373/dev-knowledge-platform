package com.ttg.devknowledgeplatform.common.service.impl;

import com.ttg.devknowledgeplatform.common.entity.SysParam;
import com.ttg.devknowledgeplatform.common.enums.ParamKey;
import com.ttg.devknowledgeplatform.common.repository.SysParamRepository;
import com.ttg.devknowledgeplatform.common.service.SysParamService;
import com.ttg.devknowledgeplatform.common.util.DateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Default {@link SysParamService} implementation, backed by {@link SysParamRepository}.
 *
 * <p>{@code findByName(key).orElseGet(SysParam::new)} — if a row exists it is updated in
 * place; if not, a new row is inserted. The {@code @Version} field on
 * {@link com.ttg.devknowledgeplatform.common.entity.AbstractEntity} guards against concurrent
 * writes if two callers race to compute the same key at the same time.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class SysParamServiceImpl implements SysParamService {

    private final SysParamRepository sysParamRepository;

    @Override
    public Optional<String> getValue(ParamKey key) {
        return sysParamRepository.findByName(key).map(SysParam::getValue);
    }

    @Override
    public void upsert(ParamKey key, String value) {
        SysParam param = sysParamRepository.findByName(key).orElseGet(SysParam::new);
        param.setName(key);
        param.setValue(value);
        param.setComputedAt(DateUtils.getCurrentDateTime());
        sysParamRepository.save(param);
    }
}
