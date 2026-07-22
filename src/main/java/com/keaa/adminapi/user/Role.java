package com.keaa.adminapi.user;

/**
 * Access roles. Stored as a string on the user row and encoded into the JWT as
 * "ROLE_<name>" so Spring Security's hasRole()/authorities line up (see JwtAuthFilter and
 * CustomUserDetailsService, which both build "ROLE_" + name).
 *
 * The tiers, highest access first:
 *   SUPER_ADMIN           — everything, incl. managing every account (the only role that can touch a Super Admin).
 *   SENIOR_ADMIN          — VPs: almost everything a Super Admin can, minus managing Super Admins.
 *   ADMIN                 — managers: every operational module + user management (not Roles & Permissions).
 *   BUSINESS_DEVELOPMENT  — customer-facing desk (the old Sales + Marketing, merged): RFQ / export / contact
 *                           leads auto-assigned by territory, plus the product catalogue and marketing content.
 *   HR                    — recruitment: job applications.
 *   EMPLOYEE              — read-only workspace (dashboard only).
 *
 * The per-endpoint rules that back this live in SecurityConfig; the mirror on the frontend
 * is src/admin/auth/roles.js (ADMIN_NAV). Keep the three in sync.
 */
public enum Role {
    SUPER_ADMIN,
    SENIOR_ADMIN,
    ADMIN,
    BUSINESS_DEVELOPMENT,
    HR,
    EMPLOYEE
}
