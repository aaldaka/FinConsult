package com.example.userservice.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

/**
 * Serves uploaded profile pictures as static resources from the upload directory (app.upload-dir).
 * Other uploads, such as KYC documents, belong to other services and are not served here.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    @Override //overriding to look and upload/serve pictures from the uploads directory
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(uploadDir, "profile_pictures").toAbsolutePath().toUri().toString();
        if (!location.endsWith("/")) {
            location += "/"; // a resource location has to end with a slash
        }
        registry.addResourceHandler("/uploads/profile_pictures/**") //only profile_pictures
                .addResourceLocations(location);
    }
}
