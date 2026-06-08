package com.ttg.devknowledgeplatform.service;

import com.ttg.devknowledgeplatform.common.enums.ContentStatus;
import com.ttg.devknowledgeplatform.common.enums.ContentType;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.ArticleResponse;
import com.ttg.devknowledgeplatform.dto.admin.CreateArticleRequest;
import com.ttg.devknowledgeplatform.dto.admin.UpdateArticleRequest;
import org.springframework.data.domain.Pageable;

public interface ArticleService {

    ArticleResponse create(CreateArticleRequest request, Integer authorId);

    ArticleResponse update(Integer id, UpdateArticleRequest request);

    ArticleResponse getById(Integer id);

    ArticleResponse getBySlug(String slug);

    PagedResponse<ArticleResponse> list(Pageable pageable, ContentType type, ContentStatus status, String q);

    void delete(Integer id);
}
