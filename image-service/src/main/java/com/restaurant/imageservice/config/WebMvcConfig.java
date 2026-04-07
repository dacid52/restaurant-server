package com.restaurant.imageservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.lang.NonNull;

import java.io.File;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${upload.dir:uploads}")
    private String uploadDir;

    // CORS is handled exclusively by the API Gateway.

    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        File dir = new File(uploadDir);
        String absolutePath = dir.getAbsolutePath() + "/";

        registry.addResourceHandler("/api/images/**")
                .addResourceLocations("file:" + absolutePath);
    }
}
