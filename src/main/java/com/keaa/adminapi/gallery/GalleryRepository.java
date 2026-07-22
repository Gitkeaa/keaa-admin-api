package com.keaa.adminapi.gallery;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GalleryRepository extends JpaRepository<GalleryItem, Long> {
    List<GalleryItem> findAllByOrderBySortOrderAscIdAsc();
}
