package com.keaa.adminapi.activity;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Records activity/login events, pulling best-effort client info off the request. */
@Service
@RequiredArgsConstructor
public class ActivityService {

    private final ActivityEventRepository repo;

    public void record(Long userId, String type, String detail, HttpServletRequest request) {
        repo.save(ActivityEvent.builder()
                .userId(userId)
                .type(type)
                .detail(detail)
                .ip(clientIp(request))
                .userAgent(shortAgent(request))
                .build());
    }

    private String clientIp(HttpServletRequest req) {
        if (req == null) return null;
        String fwd = req.getHeader("X-Forwarded-For");
        if (fwd != null && !fwd.isBlank()) return fwd.split(",")[0].trim();
        return req.getRemoteAddr();
    }

    private String shortAgent(HttpServletRequest req) {
        if (req == null) return null;
        String ua = req.getHeader("User-Agent");
        if (ua == null) return null;
        return ua.length() > 500 ? ua.substring(0, 500) : ua;
    }
}
