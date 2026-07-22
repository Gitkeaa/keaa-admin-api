package com.keaa.adminapi.download;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DownloadRepository extends JpaRepository<DownloadItem, Long> {
    List<DownloadItem> findAllByOrderByUploadedAtDesc();
}
