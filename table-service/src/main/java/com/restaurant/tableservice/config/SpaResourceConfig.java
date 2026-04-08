package com.restaurant.tableservice.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

import java.io.IOException;

@Configuration
public class SpaResourceConfig implements WebMvcConfigurer {

    /**
     * Serve plain HTML/JS/CSS cho customer web.
     *
     * Spring MVC @RestController luôn được ưu tiên hơn resource handler,
     * nên /api/** sẽ không bị chặn bởi handler này.
     *
     * Thứ tự resolve:
     *  1. File khớp chính xác (js, css, ảnh...)
     *  2. {path}/index.html (mỗi route là một thư mục có index.html)
     *  3. null → 404
     */
    @Override
    public void addResourceHandlers(@NonNull ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    @Nullable
                    protected Resource getResource(@NonNull String resourcePath, @NonNull Resource location) throws IOException {
                        Resource exact = location.createRelative(resourcePath);
                        if (exact.exists() && exact.isReadable()) {
                            return exact;
                        }
                        // Normalize trailing slash before appending /index.html to avoid double-slash
                        String base = resourcePath.endsWith("/")
                                ? resourcePath.substring(0, resourcePath.length() - 1)
                                : resourcePath;
                        Resource dirIndex = location.createRelative(base + "/index.html");
                        if (dirIndex.exists() && dirIndex.isReadable()) {
                            return dirIndex;
                        }
                        return null;
                    }
                });
    }
}
