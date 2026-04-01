package com.restaurant.orderservice.security;

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

        if (path.startsWith("/ws/")) {
            filterChain.doFilter(request, response);
            return;
        }

        String method = request.getMethod();

        // 1. Guest-allowed paths: POST /api/orders (tạo đơn khách), lấy đơn theo bàn, payments
        boolean isGuestCreate = method.equals("POST") 
                && path.equals("/api/orders");
        boolean isPaymentComplete = path.startsWith("/api/orders/payments/complete");
        boolean isGuestGetTableOrders = method.equals("GET") && path.startsWith("/api/orders/table/");
        boolean isGuestRequestPayment = method.equals("POST") && path.endsWith("/request-payment");

        if (isGuestCreate || isPaymentComplete || isGuestGetTableOrders || isGuestRequestPayment) {
            filterChain.doFilter(request, response);
            return;
        }

        // 2. Tất cả các request còn lại cần token hợp lệ
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                Claims claims = jwtUtil.extractAllClaims(token);
                request.setAttribute("userId", claims.get("id"));
                request.setAttribute("username", claims.get("username"));
                request.setAttribute("roleId", claims.get("role_id"));
                filterChain.doFilter(request, response);
                return;
            } catch (Exception e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return;
            }
        }

        // 3. Không có token → từ chối
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
