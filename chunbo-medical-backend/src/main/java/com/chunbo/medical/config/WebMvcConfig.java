package com.chunbo.medical.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

/**
 * 静态资源映射：把后端工作目录下的 uploads/ 目录暴露为 /uploads/** 静态访问，
 * 用于春播商城商品图片（上传的 + 预置下载的统一存放于 uploads/products/）。
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        java.util.List<String> locations = new java.util.ArrayList<>();
        
        java.nio.file.Path p1 = Paths.get(System.getProperty("user.dir"), "uploads").toAbsolutePath().normalize();
        locations.add(toTrailingSlashUri(p1));

        java.nio.file.Path p2 = Paths.get(System.getProperty("user.dir"), "chunbo-medical-backend", "uploads").toAbsolutePath().normalize();
        locations.add(toTrailingSlashUri(p2));

        java.nio.file.Path parent = Paths.get(System.getProperty("user.dir")).getParent();
        if (parent != null) {
            java.nio.file.Path p3 = parent.resolve("uploads").toAbsolutePath().normalize();
            locations.add(toTrailingSlashUri(p3));
            java.nio.file.Path p4 = parent.resolve("chunbo-medical-backend").resolve("uploads").toAbsolutePath().normalize();
            locations.add(toTrailingSlashUri(p4));
        }

        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(locations.toArray(new String[0]));
    }

    private String toTrailingSlashUri(java.nio.file.Path p) {
        String uri = p.toUri().toString();
        return uri.endsWith("/") ? uri : uri + "/";
    }
}
