package com.keaa.adminapi.video;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface VideoRepository extends JpaRepository<VideoItem, Long> {
    List<VideoItem> findAllByOrderBySortOrderAscIdAsc();
}
