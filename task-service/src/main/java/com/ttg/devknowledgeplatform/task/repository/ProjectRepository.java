package com.ttg.devknowledgeplatform.task.repository;

import com.ttg.devknowledgeplatform.task.entity.Project;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Integer> {

    Page<Project> findByOwnerUuid(String ownerUuid, Pageable pageable);
}
