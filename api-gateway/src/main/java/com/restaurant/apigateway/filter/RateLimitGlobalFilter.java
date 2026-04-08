package com.restaurant.apigateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rate Limiting toàn cục tại API Gateway — chống DDoS và brute-force.
 *
 * Giới hạn theo IP (lấy từ X-Forwarded-For của gateway hoặc remoteAddress):
 *  - Endpoint nhạy cảm (login/register/verify/…): 10 req / 60s
 *  - Toàn bộ API còn lại:                        200 req / 60s
 *
 * Sử dụng sliding-window in-memory (ConcurrentHashMap + ArrayDeque).
 * Không cần Redis — phù hợp single-instance. Nâng cấp lên Bucket4j+Redis khi scale.
 */
@Component
public class RateLimitGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitGlobalFilter.class);

    // --- Giới hạn Auth endpoints ---
    private static final int  AUTH_LIMIT  = 10;
    private static final long AUTH_WINDOW = 60_000L;

    // --- Giới hạn chung ---
    private static final int  GLOBAL_LIMIT  = 200;
    private static final long GLOBAL_WINDOW = 60_000L;

    private static final List<String> SENSITIVE_PREFIXES = List.of(
            "/api/users/login",
            "/api/users/register",
            "/api/users/verify",
            "/api/users/verify-otp",
            "/api/payments/request",
            "/api/payments/momo/create"
    );

    // key: "auth:{ip}" hoặc "global:{ip}"  →  sliding-window timestamps
    private final ConcurrentHashMap<String, Deque<Long>> windowMap = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String ip = resolveClientIp(exchange);
        String path = exchange.getRequest().getPath().value();
        long now = System.currentTimeMillis();

        boolean isSensitive = SENSITIVE_PREFIXES.stream().anyMatch(path::startsWith);

        if (isSensitive) {
            if (!allowRequest("auth:" + ip, AUTH_LIMIT, AUTH_WINDOW, now)) {
                return reject(exchange, "Quá nhiều yêu cầu. Vui lòng thử lại sau 1 phút.");
            }
        } else {
            if (!allowRequest("global:" + ip, GLOBAL_LIMIT, GLOBAL_WINDOW, now)) {
                log.warn("⚡ Rate limit GLOBAL vượt: ip={} path={}", ip, path);
                return reject(exchange, "Hệ thống đang bận. Vui lòng thử lại sau.");
            }
        }

        return chain.filter(exchange);
    }

    // Sliding-window check — trả về true nếu còn slot, false nếu vượt giới hạn
    private boolean allowRequest(String key, int limit, long windowMs, long now) {
        Deque<Long> ts = windowMap.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (ts) {
            while (!ts.isEmpty() && now - ts.peekFirst() > windowMs) {
                ts.pollFirst();
            }
            if (ts.size() >= limit) {
                return false;
            }
            ts.addLast(now);
        }
        return true;
    }

    private Mono<Void> reject(ServerWebExchange exchange, String message) {
        exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        byte[] body = ("{\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        DataBuffer buffer = exchange.getResponse().bufferFactory().wrap(body);
        return exchange.getResponse().writeWith(Mono.just(buffer));
    }

    /**
     * Lấy IP client thực từ X-Forwarded-For (do load-balancer phía trước set).
     * Nếu không có, dùng remoteAddress của kết nối TCP.
     */
    private String resolveClientIp(ServerWebExchange exchange) {
        String forwarded = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // Lấy IP đầu tiên (client thực) — các proxy tiếp theo append vào sau
            return forwarded.split(",")[0].trim();
        }
        var addr = exchange.getRequest().getRemoteAddress();
        return addr != null ? addr.getAddress().getHostAddress() : "unknown";
    }

    @Override
    public int getOrder() {
        // Chạy sớm nhất để từ chối trước khi gateway forward request
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
