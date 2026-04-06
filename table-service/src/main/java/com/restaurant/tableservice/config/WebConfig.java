package com.restaurant.tableservice.config;

import com.restaurant.tableservice.security.JwtAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public WebConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }
    // table-service là internal service sau API Gateway.
    // Gateway đã enforce CORS origin policy với external clients.
    // Tại đây cho phép mọi origin để không bị double-block khi request đến qua LAN IP.
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOriginPatterns("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilter() {
        FilterRegistrationBean<JwtAuthenticationFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(jwtAuthenticationFilter);
        // Cover nested table routes: /{id}/reservations, /reservations/my, /{id}/reservations/availability, ...
        registrationBean.addUrlPatterns(
                "/api/tables/*",
                "/api/tables/*/*",
                "/api/tables/*/*/*",
                "/api/tables/*/*/*/*"
        );
        return registrationBean;
    }
}

