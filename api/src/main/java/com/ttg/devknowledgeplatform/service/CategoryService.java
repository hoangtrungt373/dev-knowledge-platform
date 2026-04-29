package com.ttg.devknowledgeplatform.service;

import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.CategoryResponse;
import com.ttg.devknowledgeplatform.dto.admin.CategoryTreeNodeResponse;
import com.ttg.devknowledgeplatform.dto.admin.CreateCategoryRequest;
import com.ttg.devknowledgeplatform.dto.admin.UpdateCategoryRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface CategoryService {

    CategoryResponse create(CreateCategoryRequest request);

    CategoryResponse update(Integer id, UpdateCategoryRequest request);

    CategoryResponse getById(Integer id);

    PagedResponse<CategoryResponse> list(
            Pageable pageable, Integer parentId, Boolean rootOnly, String q);

    List<CategoryTreeNodeResponse> listTree();
}
