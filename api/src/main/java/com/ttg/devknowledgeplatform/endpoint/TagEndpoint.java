package com.ttg.devknowledgeplatform.endpoint;

import com.ttg.devknowledgeplatform.common.enums.TagStatus;
import com.ttg.devknowledgeplatform.dto.PagedResponse;
import com.ttg.devknowledgeplatform.dto.admin.CreateTagRequest;
import com.ttg.devknowledgeplatform.dto.admin.TagResponse;
import com.ttg.devknowledgeplatform.dto.admin.UpdateTagRequest;
import com.ttg.devknowledgeplatform.service.TagService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/admin/tags")
@RequiredArgsConstructor
@Slf4j
public class TagEndpoint {

    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("id", "name", "status", "dteCreation");

    private final TagService tagService;

    @PostMapping
    public ResponseEntity<TagResponse> create(@Valid @RequestBody CreateTagRequest request) {
        TagResponse response = tagService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    public ResponseEntity<TagResponse> update(
            @PathVariable Integer id, @Valid @RequestBody UpdateTagRequest request) {
        TagResponse response = tagService.update(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Integer id) {
        tagService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    public ResponseEntity<TagResponse> getById(@PathVariable Integer id) {
        TagResponse response = tagService.getById(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<PagedResponse<TagResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "desc") String sortDir,
            @RequestParam(required = false) TagStatus status,
            @RequestParam(required = false) String q) {

        Pageable pageable = PageRequest.of(page, size, buildSort(sortBy, sortDir));
        PagedResponse<TagResponse> response = tagService.list(pageable, status, q);
        return ResponseEntity.ok(response);
    }

    private Sort buildSort(String sortBy, String sortDir) {
        String field = ALLOWED_SORT_FIELDS.contains(sortBy) ? sortBy : "id";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDir) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }
}
