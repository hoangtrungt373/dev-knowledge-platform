package com.ttg.devknowledgeplatform.ecommerce.repository;

import com.ttg.devknowledgeplatform.ecommerce.entity.OutboxEvent;
import com.ttg.devknowledgeplatform.ecommerce.enums.OutboxEventStatus;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Integer> {

    @Query("SELECT e.id FROM OutboxEvent e WHERE e.status = :status ORDER BY e.id ASC")
    List<Integer> findIdsByStatus(@Param("status") OutboxEventStatus status, Pageable pageable);

    /**
     * Atomically claims a row for processing by flipping {@code status} from {@code from} to
     * {@code to} only if it still matches {@code from} — the relay's guard against two callers
     * (e.g. two instances, or a retry racing the original attempt) dispatching the same event
     * twice. Returns the number of rows updated: {@code 1} if this call won the claim, {@code 0}
     * if someone else already claimed or finished it first.
     */
    @Modifying
    @Query("UPDATE OutboxEvent e SET e.status = :to WHERE e.id = :id AND e.status = :from")
    int claim(@Param("id") Integer id, @Param("from") OutboxEventStatus from, @Param("to") OutboxEventStatus to);
}
