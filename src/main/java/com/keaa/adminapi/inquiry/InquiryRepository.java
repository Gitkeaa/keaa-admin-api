package com.keaa.adminapi.inquiry;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InquiryRepository extends JpaRepository<Inquiry, Long> {
    List<Inquiry> findAllByOrderByCreatedAtDesc();
    List<Inquiry> findByAssignedUserIdOrderByCreatedAtDesc(Long assignedUserId);
    long countByAssignedUserId(Long assignedUserId);
}
