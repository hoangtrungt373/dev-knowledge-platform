package com.ttg.devknowledgeplatform.infra.scheduling;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

/** Load, re-check, change, save — and the proxy-friendliness the transaction depends on. */
class AbstractGuardedUpdateProcessorTest {

    @SuppressWarnings("unchecked")
    private final CrudRepository<Item, Integer> repository = mock(CrudRepository.class);
    private final TestProcessor processor = new TestProcessor(repository);

    @Test
    void anEligibleEntityIsChangedAndSaved() {
        Item item = new Item(true);
        when(repository.findById(1)).thenReturn(Optional.of(item));

        assertThat(processor.process(1)).isTrue();

        assertThat(item.changed).isTrue();
        verify(repository).save(item);
    }

    @Test
    void anEntityThatNoLongerQualifiesIsLeftAlone() {
        Item item = new Item(false);
        when(repository.findById(1)).thenReturn(Optional.of(item));

        assertThat(processor.process(1)).isFalse();

        assertThat(item.changed).isFalse();
        verify(repository, never()).save(any());
    }

    @Test
    void aMissingEntityIsSkipped() {
        when(repository.findById(1)).thenReturn(Optional.empty());

        assertThat(processor.process(1)).isFalse();
        verify(repository, never()).save(any());
    }

    /**
     * Guards the one easy-to-break property: a {@code final} or non-public {@code process} would
     * silently lose its transaction under Spring's CGLIB proxy, with no error at all.
     */
    @Test
    void processStaysPublicNonFinalAndTransactionalSoTheProxyCanWrapIt() throws NoSuchMethodException {
        Method process = AbstractGuardedUpdateProcessor.class.getMethod("process", Object.class);

        assertThat(Modifier.isFinal(process.getModifiers())).as("final defeats the CGLIB proxy").isFalse();
        assertThat(process.getAnnotation(Transactional.class).rollbackFor()).containsExactly(Throwable.class);
    }

    private static final class Item {
        private final boolean eligible;
        private boolean changed;

        Item(boolean eligible) {
            this.eligible = eligible;
        }
    }

    private static final class TestProcessor extends AbstractGuardedUpdateProcessor<Item, Integer> {
        private final CrudRepository<Item, Integer> repository;

        TestProcessor(CrudRepository<Item, Integer> repository) {
            this.repository = repository;
        }

        @Override
        protected CrudRepository<Item, Integer> repository() {
            return repository;
        }

        @Override
        protected boolean isStillEligible(Item item) {
            return item.eligible;
        }

        @Override
        protected void apply(Item item) {
            item.changed = true;
        }
    }
}
