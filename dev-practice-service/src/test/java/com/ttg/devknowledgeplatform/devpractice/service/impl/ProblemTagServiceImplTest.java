package com.ttg.devknowledgeplatform.devpractice.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.ttg.devknowledgeplatform.common.exception.BusinessException;
import com.ttg.devknowledgeplatform.devpractice.entity.ProblemTag;
import com.ttg.devknowledgeplatform.devpractice.exception.DevPracticeErrorCode;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemTagAssignmentRepository;
import com.ttg.devknowledgeplatform.devpractice.repository.ProblemTagRepository;
import com.ttg.devknowledgeplatform.infra.service.SlugService;

/** Name uniqueness, slug handling on rename, and the delete-in-use guard. */
class ProblemTagServiceImplTest {

    private final ProblemTagRepository tagRepository = mock(ProblemTagRepository.class);
    private final ProblemTagAssignmentRepository assignmentRepository = mock(ProblemTagAssignmentRepository.class);
    private final SlugService slugService = mock(SlugService.class);
    private final ProblemTagServiceImpl service =
            new ProblemTagServiceImpl(tagRepository, assignmentRepository, slugService);

    @Test
    void createTrimsTheNameAndGeneratesASlug() {
        when(tagRepository.existsByNameIgnoreCase("Two Pointers")).thenReturn(false);
        when(slugService.generateUniqueSlug(eq("Two Pointers"), any(), eq(DevPracticeErrorCode.PROBLEM_TAG_SLUG_CONFLICT)))
                .thenReturn("two-pointers");
        when(tagRepository.save(any(ProblemTag.class))).thenAnswer(inv -> inv.getArgument(0));

        ProblemTag created = service.create("  Two Pointers ");

        assertThat(created.getName()).isEqualTo("Two Pointers");
        assertThat(created.getSlug()).isEqualTo("two-pointers");
    }

    @Test
    void createRejectsAnExistingNameCaseInsensitively() {
        when(tagRepository.existsByNameIgnoreCase("array")).thenReturn(true);

        assertThatThrownBy(() -> service.create("array"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("A tag named 'array' already exists");
    }

    @Test
    void aCaseOnlyRenameKeepsTheSlug() {
        ProblemTag tag = tag(1, "array", "array");
        when(tagRepository.findById(1)).thenReturn(Optional.of(tag));
        when(tagRepository.save(tag)).thenReturn(tag);

        service.update(1, "Array");

        assertThat(tag.getName()).isEqualTo("Array");
        assertThat(tag.getSlug()).isEqualTo("array");
        verify(slugService, never()).generateUniqueSlug(anyString(), any(java.util.function.BiPredicate.class), any(), any());
    }

    @Test
    void deleteIsRefusedWhileTheTagIsInUse() {
        ProblemTag tag = tag(1, "Array", "array");
        when(tagRepository.findById(1)).thenReturn(Optional.of(tag));
        when(assignmentRepository.countByProblemTag_Id(1)).thenReturn(4L);

        assertThatThrownBy(() -> service.delete(1))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(DevPracticeErrorCode.PROBLEM_TAG_IN_USE))
                .hasMessageContaining("used by 4 problem(s)");
        verify(tagRepository, never()).delete(tag);
    }

    @Test
    void deletesAnUnusedTag() {
        ProblemTag tag = tag(1, "Array", "array");
        when(tagRepository.findById(1)).thenReturn(Optional.of(tag));
        when(assignmentRepository.countByProblemTag_Id(1)).thenReturn(0L);

        service.delete(1);

        verify(tagRepository).delete(tag);
    }

    private static ProblemTag tag(int id, String name, String slug) {
        ProblemTag tag = ProblemTag.builder().name(name).slug(slug).build();
        tag.setId(id);
        return tag;
    }
}
