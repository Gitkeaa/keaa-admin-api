package com.keaa.adminapi.config;

import com.keaa.adminapi.security.JwtAuthFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    @Value("${app.cors.allowed-origins}")
    private List<String> allowedOrigins;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors(withDefaults()) // uses the CorsConfigurationSource bean below
                .csrf(AbstractHttpConfigurer::disable) // stateless JWT API, no CSRF token
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**").permitAll()
                        // Uploaded avatars/files are public (referenced from <img> tags).
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        // Public site forms submit these without logging in; reading and
                        // updating them still requires an authenticated admin. Listed BEFORE the
                        // role rules below so the public POST wins over "/api/<x>/**".
                        .requestMatchers(HttpMethod.POST, "/api/rfq", "/api/contact", "/api/careers").permitAll()

                        // ---- Role-based access. Mirror of ADMIN_NAV in the frontend roles.js;
                        //      keep the two in step. hasRole("X") matches the "ROLE_X" authority
                        //      JwtAuthFilter builds from the token's role claim. ----

                        // User management: Admin + HR may VIEW the team; only Super/Senior Admin may change it.
                        .requestMatchers(HttpMethod.GET, "/api/users/**").hasAnyRole("SUPER_ADMIN", "SENIOR_ADMIN", "ADMIN", "HR")
                        .requestMatchers("/api/users/**").hasAnyRole("SUPER_ADMIN", "SENIOR_ADMIN")
                        .requestMatchers("/api/settings/**").hasAnyRole("SUPER_ADMIN", "SENIOR_ADMIN")

                        // Products: Business Development may VIEW; the admin tiers manage.
                        .requestMatchers(HttpMethod.GET, "/api/products/**").hasAnyRole("SUPER_ADMIN", "SENIOR_ADMIN", "ADMIN", "BUSINESS_DEVELOPMENT")
                        .requestMatchers("/api/products/**").hasAnyRole("SUPER_ADMIN", "SENIOR_ADMIN", "ADMIN")

                        // Product categories: Admin + Business Development may VIEW; only Super/Senior manage.
                        .requestMatchers(HttpMethod.GET, "/api/categories/**").hasAnyRole("SUPER_ADMIN", "SENIOR_ADMIN", "ADMIN", "BUSINESS_DEVELOPMENT")
                        .requestMatchers("/api/categories/**").hasAnyRole("SUPER_ADMIN", "SENIOR_ADMIN")

                        // Gallery + Videos: Business Development may VIEW; the admin tiers manage.
                        .requestMatchers(HttpMethod.GET, "/api/gallery/**").hasAnyRole("SUPER_ADMIN", "SENIOR_ADMIN", "ADMIN", "BUSINESS_DEVELOPMENT")
                        .requestMatchers("/api/gallery/**").hasAnyRole("SUPER_ADMIN", "SENIOR_ADMIN", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/videos/**").hasAnyRole("SUPER_ADMIN", "SENIOR_ADMIN", "ADMIN", "BUSINESS_DEVELOPMENT")
                        .requestMatchers("/api/videos/**").hasAnyRole("SUPER_ADMIN", "SENIOR_ADMIN", "ADMIN")

                        .requestMatchers("/api/downloads/**").hasAnyRole("SUPER_ADMIN", "SENIOR_ADMIN", "ADMIN", "BUSINESS_DEVELOPMENT")
                        .requestMatchers("/api/translations/**").hasAnyRole("SUPER_ADMIN", "SENIOR_ADMIN", "ADMIN", "BUSINESS_DEVELOPMENT")

                        // Lead pipeline — admin tiers + the Business Development reps who work leads.
                        // Assign is Admin-only and status updates are assignee-only (both in the controller).
                        .requestMatchers("/api/inquiries/**").hasAnyRole("SUPER_ADMIN", "SENIOR_ADMIN", "ADMIN", "BUSINESS_DEVELOPMENT")
                        .requestMatchers("/api/rfq/**").hasAnyRole("SUPER_ADMIN", "SENIOR_ADMIN", "ADMIN", "BUSINESS_DEVELOPMENT")
                        .requestMatchers("/api/contact/**").hasAnyRole("SUPER_ADMIN", "SENIOR_ADMIN", "ADMIN", "BUSINESS_DEVELOPMENT")
                        // Recruitment. Everyone here may VIEW applications, but only HR may change an
                        // application's status — the admin tiers are view-only for recruitment.
                        .requestMatchers(HttpMethod.PATCH, "/api/careers/**").hasRole("HR")
                        .requestMatchers("/api/careers/**").hasAnyRole("SUPER_ADMIN", "SENIOR_ADMIN", "ADMIN", "HR")
                        // Dashboard KPIs: any signed-in staff member (each renders a role-shaped view).
                        .requestMatchers("/api/dashboard/**").authenticated()

                        // SOP / Help guides: everyone signed in reads; only the top tiers edit.
                        .requestMatchers(HttpMethod.GET, "/api/sops/**").authenticated()
                        .requestMatchers("/api/sops/**").hasAnyRole("SUPER_ADMIN", "SENIOR_ADMIN")

                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        // setAllowedOriginPatterns (not setAllowedOrigins) so an entry may carry a port
        // wildcard like http://localhost:* — Vite's dev port and the preview port differ,
        // and localhost vs 127.0.0.1 are two distinct origins a browser will not treat as
        // the same. Patterns still work with allowCredentials(true), which a bare "*" cannot.
        config.setAllowedOriginPatterns(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setAllowCredentials(true); // required so the browser sends the httpOnly cookie
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
