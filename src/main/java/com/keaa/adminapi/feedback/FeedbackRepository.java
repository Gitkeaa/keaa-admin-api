package com.keaa.adminapi.feedback;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findAllByOrderByCreatedAtDesc();
    long countByStatus(String status);

    /**
     * Mean star rating across every row, or null when the table is empty — the dashboard tile
     * needs the distinction, because "0.0 out of 5" and "nobody has rated us yet" are very
     * different things to show a manager.
     */
    @Query("SELECT AVG(f.rating) FROM Feedback f")
    Double averageRating();
}
