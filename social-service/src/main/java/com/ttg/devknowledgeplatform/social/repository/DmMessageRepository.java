package com.ttg.devknowledgeplatform.social.repository;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ttg.devknowledgeplatform.social.entity.DmMessage;
import com.ttg.devknowledgeplatform.social.entity.DmThread;

/** Repository for {@link DmMessage} — paginated history lookup for a single {@link DmThread} (US-7). */
@Repository
public interface DmMessageRepository extends JpaRepository<DmMessage, Integer> {

    Page<DmMessage> findByDmThreadOrderByDteCreationDesc(DmThread dmThread, Pageable pageable);

    /**
     * Seed-only: overwrites {@code dteCreation} after the row already exists, so
     * {@code DmThreadSeeder} can backdate sample messages across a spread of timestamps. A normal
     * entity-managed update can't do this — {@code AbstractEntity}'s {@code @PrePersist} always
     * resets {@code dteCreation} to "now" on {@code save()}, and the column is
     * {@code updatable = false} for any subsequent JPA update. A JPQL bulk update is a direct DML
     * statement, not a dirty-checked entity update, so it bypasses both. Never call this outside
     * seeding.
     */
    @Modifying
    @Query("UPDATE DmMessage m SET m.dteCreation = :dteCreation WHERE m.id = :id")
    void backdateCreatedAt(@Param("id") Integer id, @Param("dteCreation") Instant dteCreation);
}
