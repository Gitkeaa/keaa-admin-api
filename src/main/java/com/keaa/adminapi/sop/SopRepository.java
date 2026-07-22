package com.keaa.adminapi.sop;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SopRepository extends JpaRepository<Sop, Long> {
    Optional<Sop> findByScopeAndRefKey(String scope, String refKey);
}
