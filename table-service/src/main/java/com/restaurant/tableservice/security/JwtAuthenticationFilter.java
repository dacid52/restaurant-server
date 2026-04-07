package com.restaurant.tableservice.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String ADMIN_ROLE = "ADMIN";
    private static final String STAFF_ROLE = "STAFF";
    private static final String CUSTOMER_ROLE = "CUSTOMER";

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

        String path = request.getRequestURI();
        String method = request.getMethod();

        if ("OPTIONS".equalsIgnoreCase(method)) {
            filterChain.doFilter(request, response);
            return;
        }

        boolean publicEndpoint = isPublicEndpoint(path, method);
        String authHeader = request.getHeader("Authorization");

        if (!publicEndpoint && (authHeader == null || !authHeader.startsWith("Bearer "))) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
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

            if (CUSTOMER_ROLE.equalsIgnoreCase(roleName)) {
                boolean customerAllowed = isCustomerAllowed(path, method) || publicEndpoint;
                if (!customerAllowed) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
            } else if (!publicEndpoint) {
                boolean staffAllowed = ADMIN_ROLE.equalsIgnoreCase(roleName)
                        || STAFF_ROLE.equalsIgnoreCase(roleName)
                        || "MANAGER".equalsIgnoreCase(roleName)
                        || "CASHIER".equalsIgnoreCase(roleName);
                if (!staffAllowed) {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    return;
                }
            }
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isPublicEndpoint(String path, String method) {
        if (path.contains("generate-access") || path.contains("validate-key")) {
            return true;
        }

        if (!"GET".equals(method)) {
            return false;
        }

        return path.equals("/api/tables")
                || path.matches("/api/tables/\\d+")
                || path.matches("/api/tables/\\d+/reservations/availability");
    }

    private boolean isCustomerAllowed(String path, String method) {
        return ("POST".equals(method) && path.matches("/api/tables/\\d+/reservations"))
                || path.endsWith("/reservations/my");
    }

    private boolean isTrustedInternalCall(HttpServletRequest request) {
        String header = request.getHeader("X-Service-Token");
        return internalServiceToken != null
                && !internalServiceToken.isBlank()
                && internalServiceToken.equals(header);
    }
}
