package com.restaurant.userservice.config;

import com.restaurant.userservice.security.JwtAuthenticationFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.lang.NonNull;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RateLimitInterceptor rateLimitInterceptor;

    public WebConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                     RateLimitInterceptor rateLimitInterceptor) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    // CORS is handled exclusively by the API Gateway.
    // Do NOT add addCorsMappings here — doing so causes duplicate
    // Access-Control-Allow-Origin headers when the gateway also sets the header.

        // BUG-023: Rate limiting cho các endpoint đăng nhập / đăng ký / verify-email / verify-otp
    @Override
    @SuppressWarnings("null")
    public void addInterceptors(@NonNull InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns(
                        "/api/users/login",
                        "/api/users/register",
                "/api/users/verify-email",
                "/api/users/verify-otp"
                );
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> jwtFilter() {
        FilterRegistrationBean<JwtAuthenticationFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(jwtAuthenticationFilter);
        registrationBean.addUrlPatterns("/api/users/*", "/api/users/me", "/api/users/logout");
        return registrationBean;
    }
}
