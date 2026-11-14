package com.example.documentservice;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@SpringBootApplication
@EnableJpaAuditing(auditorAwareRef = "auditorAware") //createdby updatedby, the auditor comes from SecurityAuditorAware
public class DocumentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(DocumentServiceApplication.class, args);
    }

}
