package com.restaurant.kitchenservice.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FeignClientInterceptor implements RequestInterceptor {

    @Value("${internal.service-token:}")
    private String internalServiceToken;

    @Override
    public void apply(RequestTemplate requestTemplate) {
        if (internalServiceToken != null && !internalServiceToken.isBlank()) {
            requestTemplate.header("X-Service-Token", internalServiceToken);
        }
    }
}
