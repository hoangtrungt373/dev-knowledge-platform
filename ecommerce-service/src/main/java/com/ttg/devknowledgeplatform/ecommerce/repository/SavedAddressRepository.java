package com.ttg.devknowledgeplatform.ecommerce.repository;

import com.ttg.devknowledgeplatform.ecommerce.entity.SavedAddress;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedAddressRepository extends JpaRepository<SavedAddress, Integer> {

    /** Default-first, then most-recently-created — used by {@code SavedAddressServiceImpl#list}. */
    List<SavedAddress> findByOwnerUuidOrderByDefaultAddressDescIdDesc(String ownerUuid);

    /** Ownership-scoped lookup — mirrors {@code OrderRepository}'s own
     * {@code findById(id).filter(o -> o.getOwnerUuid().equals(callerUuid))} pattern, just as a
     * derived query instead. */
    Optional<SavedAddress> findByIdAndOwnerUuid(Integer id, String ownerUuid);

    /**
     * Bulk-unsets any existing default for {@code ownerUuid} in one statement, rather than loading
     * and re-saving whatever row currently holds it — used by {@code SavedAddressServiceImpl}
     * right before setting a *different* row's flag to {@code true}, so the partial unique index
     * ({@code UX_SAVED_ADDRESS_OWNER_DEFAULT}) never sees two {@code true} rows for the same owner
     * at once, even transiently within the same transaction.
     */
    @Modifying
    @Query("UPDATE SavedAddress a SET a.defaultAddress = false WHERE a.ownerUuid = :ownerUuid AND a.defaultAddress = true")
    void clearDefaultForOwner(@Param("ownerUuid") String ownerUuid);
}
