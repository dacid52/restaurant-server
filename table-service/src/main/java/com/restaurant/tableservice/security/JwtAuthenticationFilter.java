package com.restaurant.tableservice.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.lang.NonNull;

import java.io.IOException;
import org.springframework.stereotype.Component;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        if ("true".equals(request.getHeader("X-Internal-Call"))) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();
        String method = request.getMethod();

        // Allowed paths — không cần token
        if (path.contains("validate-key") || path.contains("qr/static") || path.contains("qr/dynamic")) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        // /reservations/my — bắt buộc có token (customer xem lịch sử đặt bàn)
        if (path.endsWith("/reservations/my")) {
            if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
            // token validated below — skip the anonymous GET shortcut
        } else if (method.equals("GET") && (authHeader == null || !authHeader.startsWith("Bearer "))) {
            // GET requests không cần token (xem danh sách bàn, check availability)
            filterChain.doFilter(request, response);
            return;
        }

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

            Integer roleId = ((Number) claims.get("role_id")).intValue();

            // Quyền theo role:
            // role 1-4 (ADMIN/MANAGER/CASHIER/WAITER): full access
            // role 5 (CUSTOMER): chỉ được POST /tables/{id}/reservations và GET /reservations/my
            boolean isCustomer = (roleId == 5);

            if (isCustomer) {
                boolean allowedForCustomer = (method.equals("POST") && path.matches(".*/tables/\\d+/reservations"))
                        || path.endsWith("/reservations/my");
                if (!allowedForCustomer) {
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
}
