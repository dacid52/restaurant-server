package com.restaurant.tableservice.config;

import com.restaurant.tableservice.security.JwtAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    public WebConfig(JwtAuthenticationFilter jwtAuthenticationFilter) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
    }
    // CORS is handled exclusively by the API Gateway.
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

