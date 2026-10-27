package com.example.finConsult;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("dev")
@TestPropertySource(properties = "spring.datasource.url=jdbc:postgresql://localhost:5432/finconsult_test")
class FinConsultApplicationTests {

	@Test
	void contextLoads() {
	}

}
