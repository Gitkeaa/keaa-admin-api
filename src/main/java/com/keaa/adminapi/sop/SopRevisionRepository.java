package com.keaa.adminapi.sop;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SopRevisionRepository extends JpaRepository<SopRevision, Long> {
    List<SopRevision> findBySopIdOrderByCreatedAtDesc(Long sopId);
}
