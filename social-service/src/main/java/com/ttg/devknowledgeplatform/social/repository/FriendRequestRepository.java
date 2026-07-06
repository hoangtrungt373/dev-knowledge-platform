package com.ttg.devknowledgeplatform.social.repository;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ttg.devknowledgeplatform.common.entity.User;
import com.ttg.devknowledgeplatform.social.entity.FriendRequest;
import com.ttg.devknowledgeplatform.social.enums.FriendRequestStatus;

@Repository
public interface FriendRequestRepository extends JpaRepository<FriendRequest, Integer> {

    Optional<FriendRequest> findByRequesterAndAddresseeAndStatus(
            User requester, User addressee, FriendRequestStatus status);

    Page<FriendRequest> findByAddresseeAndStatus(User addressee, FriendRequestStatus status, Pageable pageable);

    Page<FriendRequest> findByRequesterAndStatus(User requester, FriendRequestStatus status, Pageable pageable);

    @Query("SELECT fr FROM FriendRequest fr WHERE fr.status = 'PENDING' "
            + "AND ((fr.requester = :a AND fr.addressee = :b) OR (fr.requester = :b AND fr.addressee = :a))")
    Optional<FriendRequest> findPendingBetween(@Param("a") User a, @Param("b") User b);
}
