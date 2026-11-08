package com.example.finConsult;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test") // CHANGED: own test profile, so the dev DataSeeder does not run
@TestPropertySource(locations = "classpath:test.properties") // CHANGED: test-only settings with env-variable overrides
class FinConsultApplicationTests {

	@Test
	void contextLoads() {
	}

}
