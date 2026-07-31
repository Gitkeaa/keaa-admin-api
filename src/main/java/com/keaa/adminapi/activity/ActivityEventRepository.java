package com.keaa.adminapi.activity;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ActivityEventRepository extends JpaRepository<ActivityEvent, Long> {
    List<ActivityEvent> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    List<ActivityEvent> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, String type, Pageable pageable);
}
  