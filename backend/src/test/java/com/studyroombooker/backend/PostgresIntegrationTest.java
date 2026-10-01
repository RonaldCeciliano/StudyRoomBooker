package com.studyroombooker.backend;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Reusable full-application PostgreSQL test setup; Docker must be running. */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class PostgresIntegrationTest {
}
