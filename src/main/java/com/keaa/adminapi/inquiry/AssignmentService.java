package com.keaa.adminapi.inquiry;

import com.keaa.adminapi.user.Role;
import com.keaa.adminapi.user.User;
import com.keaa.adminapi.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

/**
 * Picks the Sales/Marketing member who owns an inquiry's territory. A candidate must own BOTH
 * the inquiry's country AND its product category; among the matches the least-loaded one wins,
 * so leads spread evenly. Returns null when nobody's territory fits (the inquiry stays
 * unassigned for an admin to route by hand).
 */
@Service
@RequiredArgsConstructor
public class AssignmentService {

    private final UserRepository userRepository;
    private final InquiryRepository inquiryRepository;

    public User findAssignee(String country, String category) {
        List<User> candidates = userRepository.findAll().stream()
                .filter(User::isActive)
                .filter(u -> u.getRole() == Role.BUSINESS_DEVELOPMENT)
                .filter(u -> owns(u.getAssignedCountries(), country) && owns(u.getAssignedCategories(), category))
                .toList();
        if (candidates.isEmpty()) return null;
        return candidates.stream()
                .min(Comparator.comparingLong(u -> inquiryRepository.countByAssignedUserId(u.getId())))
                .orElse(null);
    }

    /** True if `value` is one of the comma-joined entries in `csv` (case-insensitive). */
    private boolean owns(String csv, String value) {
        if (csv == null || value == null || value.isBlank()) return false;
        for (String part : csv.split(",")) {
            if (part.trim().equalsIgnoreCase(value.trim())) return true;
        }
        return false;
    }
}
