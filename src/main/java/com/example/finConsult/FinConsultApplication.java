package com.example.finConsult;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableAsync
@EnableScheduling
@EnableJpaAuditing(auditorAwareRef = "auditorAware") //createdby updatedby
public class FinConsultApplication {

	public static void main(String[] args) {
		SpringApplication.run(FinConsultApplication.class, args);
	}

}
