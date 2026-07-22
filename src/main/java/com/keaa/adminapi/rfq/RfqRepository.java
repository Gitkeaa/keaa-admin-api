package com.keaa.adminapi.rfq;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RfqRepository extends JpaRepository<RfqRequest, Long> {
    List<RfqRequest> findAllByOrderByCreatedAtDesc();
    long countByStatus(String status);
}
