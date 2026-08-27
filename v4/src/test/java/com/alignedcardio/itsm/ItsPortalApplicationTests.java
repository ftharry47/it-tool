package com.alignedcardio.itsm;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
@Disabled("Docker is not installed in this environment; Testcontainers cannot start")
class ItsPortalApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that Spring Boot, JPA, Flyway and the base schema start cleanly
        // against a Testcontainers PostgreSQL instance.
    }
}
