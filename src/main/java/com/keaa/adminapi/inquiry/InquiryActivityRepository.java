package com.keaa.adminapi.inquiry;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InquiryActivityRepository extends JpaRepository<InquiryActivity, Long> {
    List<InquiryActivity> findByInquiryIdOrderByCreatedAtAsc(Long inquiryId);
}
