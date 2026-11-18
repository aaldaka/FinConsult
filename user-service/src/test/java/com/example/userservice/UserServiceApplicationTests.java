package com.example.userservice;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

// runs on finconsult_test (TEST_DB_URL), which docker/db/init/00-create-test-db.sh builds. With ddl-auto=validate it also
// checks that the users, profiles, advisors and token tables match the entities
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class UserServiceApplicationTests {

    @Test
    void contextLoads() {
    }

}
