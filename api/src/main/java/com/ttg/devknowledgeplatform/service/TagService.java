package com.ttg.devknowledgeplatform.service;

import com.ttg.devknowledgeplatform.common.enums.TagStatus;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.CreateTagRequest;
import com.ttg.devknowledgeplatform.dto.admin.TagResponse;
import com.ttg.devknowledgeplatform.dto.admin.UpdateTagRequest;
import org.springframework.data.domain.Pageable;

public interface TagService {

    TagResponse create(CreateTagRequest request);

    TagResponse update(Integer id, UpdateTagRequest request);

    TagResponse getById(Integer id);

    PagedResponse<TagResponse> list(Pageable pageable, TagStatus status, String q);
}
