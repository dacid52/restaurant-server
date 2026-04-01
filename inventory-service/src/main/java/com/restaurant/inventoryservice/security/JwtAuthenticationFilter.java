package com.restaurant.inventoryservice.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.lang.NonNull;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;

    public JwtAuthenticationFilter(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response, @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        // Allow internal Feign calls from other microservices (e.g. kitchen-service batch-deduct)
        if ("true".equals(request.getHeader("X-Internal-Call"))) {
            filterChain.doFilter(request, response);
            return;
        }

        String path = request.getRequestURI();

        // Also bypass /details path (legacy, keep for safety)
        if (path.contains("/details")) {
            filterChain.doFilter(request, response);
            return;
        }

        // GET requests are public
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
            
            Integer roleId = (Integer) claims.get("role_id");
            // Admin (1), Manager (2) and Inventory Staff (usually)
            if (roleId != 1 && roleId != 2) {
                 response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                 return;
            }

        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }

        filterChain.doFilter(request, response);
    }
}
