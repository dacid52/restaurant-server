package com.restaurant.menuservice.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.lang.NonNull;

import java.io.IOException;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String ADMIN_ROLE = "ADMIN";

    private final JwtUtil jwtUtil;
    private final String internalServiceToken;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, @Value("${internal.service-token:}") String internalServiceToken) {
        this.jwtUtil = jwtUtil;
        this.internalServiceToken = internalServiceToken;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if (isTrustedInternalCall(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        // GET routes are mostly public or handled by gateway mapping
        if (request.getMethod().equals("GET")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = jwtUtil.extractAllClaims(token);
            request.setAttribute("userId", claims.get("id"));
            request.setAttribute("username", claims.get("username"));
            request.setAttribute("roleId", claims.get("role_id"));
            String roleName = claims.get("role_name", String.class);
            request.setAttribute("roleName", roleName);

            // ADMIN, MANAGER, KITCHEN (+ STAFF for backward compat) can modify menu
            boolean canModifyMenu = ADMIN_ROLE.equalsIgnoreCase(roleName)
                    || "MANAGER".equalsIgnoreCase(roleName)
                    || "KITCHEN".equalsIgnoreCase(roleName)
                    || "STAFF".equalsIgnoreCase(roleName);
            if (!canModifyMenu) {
                 response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                 return;
            }

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isTrustedInternalCall(HttpServletRequest request) {
        String header = request.getHeader("X-Service-Token");
        return internalServiceToken != null
                && !internalServiceToken.isBlank()
                && internalServiceToken.equals(header);
    }
}
