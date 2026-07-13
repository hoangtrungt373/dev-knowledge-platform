package com.ttg.devknowledgeplatform.social.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ttg.devknowledgeplatform.social.entity.DmMessage;
import com.ttg.devknowledgeplatform.social.entity.DmThread;

/** Repository for {@link DmMessage} — paginated history lookup for a single {@link DmThread} (US-7). */
@Repository
public interface DmMessageRepository extends JpaRepository<DmMessage, Integer> {

    Page<DmMessage> findByDmThreadOrderByDteCreationDesc(DmThread dmThread, Pageable pageable);
}
