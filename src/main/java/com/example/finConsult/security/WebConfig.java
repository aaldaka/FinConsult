package com.example.finConsult.security;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    @Override //overriding to look and upload/serve pictures from the uploads directory
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/profile_pictures/**") //only profile_pictures NO KYC DOCS
                .addResourceLocations("file:uploads/profile_pictures/");
    }
}
