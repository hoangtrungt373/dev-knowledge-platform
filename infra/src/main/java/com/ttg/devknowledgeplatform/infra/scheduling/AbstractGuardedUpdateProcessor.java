package com.ttg.devknowledgeplatform.infra.scheduling;

import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * GoF <b>Template Method</b> (Behavioral) for the per-item half of an {@link AbstractBatchJob}: in
 * one transaction, load an entity, re-check it still qualifies, change it, save it. A batch job's id
 * list is a snapshot — by the time an id is processed, someone else may have handled it — so the
 * re-check ({@link #isStillEligible}) inside the transaction is what makes the change safe. Pair it
 * with the entity's {@code @Version} column: a concurrent writer that commits between the load and
 * this commit makes the commit fail, which {@link AbstractBatchJob} counts as "skipped".
 *
 * <p>A subclass is a Spring bean that supplies {@link #repository()}, the guard, and the change, and
 * the batch job calls {@link #process(Object)} on it — through the bean's proxy, so the
 * transaction applies.
 *
 * <p><b>Why {@link #process(Object)} isn't {@code final}</b> (though a template method usually is):
 * Spring applies {@code @Transactional} by subclassing the bean at runtime (a CGLIB proxy) and
 * overriding the annotated method to open and commit the transaction around it. A {@code final}
 * method can't be overridden, so the proxy would quietly call it with <i>no transaction at all</i> —
 * no error, no warning. Subclasses shouldn't override it either; override the steps instead.
 *
 * @param <T>  the entity type
 * @param <ID> its id type
 */
public abstract class AbstractGuardedUpdateProcessor<T, ID> {

    /**
     * Loads the entity, and if it is still eligible, applies the change and saves it — all in one
     * transaction.
     *
     * @return {@code true} if it changed the entity, {@code false} if it was gone or no longer eligible
     * @throws org.springframework.dao.OptimisticLockingFailureException if another writer changed the
     *         row first (the commit fails; the caller treats it as skipped)
     */
    @Transactional(rollbackFor = Throwable.class)
    public boolean process(ID id) {
        T entity = repository().findById(id).orElse(null);
        if (entity == null || !isStillEligible(entity)) {
            return false;
        }
        apply(entity);
        repository().save(entity);
        return true;
    }

    /** The repository the entity is loaded from and saved to. */
    protected abstract CrudRepository<T, ID> repository();

    /** Re-checks, inside the transaction, that the entity still needs the change. */
    protected abstract boolean isStillEligible(T entity);

    /** The change itself — mutate the entity; saving is done for you. */
    protected abstract void apply(T entity);
}
