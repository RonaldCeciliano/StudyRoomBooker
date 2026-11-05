package com.studyroombooker.backend;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.HashSet;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "spring.flyway.locations=classpath:db/migration,classpath:db/dev")
class ShiftsApiTests extends PostgresIntegrationTest {
    @Value("${local.server.port}") int port;
    @Autowired JdbcTemplate jdbc;
    @Autowired Flyway flyway;

    @Test
    void servesDatabaseFixturesMatchingContractAndDoesNotReseedOnRestart() throws Exception {
        try (var client = HttpClient.newHttpClient()) {
            var response = get(client);
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/json");
            var json = JsonMapper.builder().build().readTree(response.body());
            assertThat(json.isArray()).isTrue();
            assertThat(json.size()).isEqualTo(3);
            String[] statuses = {"BOOKED", "ASSIGNED", "UNASSIGNED"};
            Instant previous = Instant.MIN;
            for (int i = 0; i < 3; i++) {
                var shift = json.get(i);
                var fields = new HashSet<String>();
                shift.propertyNames().forEach(fields::add);
                assertThat(fields).containsExactlyInAnyOrder("id", "startsAt", "endsAt", "status", "plannedRoomCode", "memberName");
                assertThat(shift.get("id").isIntegralNumber()).isTrue();
                assertThat(shift.get("status").asString()).isEqualTo(statuses[i]);
                assertThat(shift.get("plannedRoomCode").asString()).isEqualTo("DEMO-A");
                String start = shift.get("startsAt").asString();
                String end = shift.get("endsAt").asString();
                assertThat(start).endsWith("Z");
                assertThat(end).endsWith("Z");
                assertThat(Instant.parse(start)).isAfterOrEqualTo(previous);
                assertThat(Instant.parse(end)).isAfter(Instant.parse(start));
                previous = Instant.parse(start);
            }
            assertThat(json.get(0).get("memberName").asString()).isEqualTo("Ronald (Demo)");
            assertThat(json.get(1).get("memberName").asString()).isEqualTo("Genaro (Demo)");
            assertThat(json.get(2).get("memberName").isNull()).isTrue();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM library_hours", Integer.class)).isEqualTo(7);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM study_block_backup_room", Integer.class)).isEqualTo(1);

            jdbc.update("DELETE FROM shift");
            flyway.migrate();
            assertThat(get(client).body()).isEqualTo("[]");

            jdbc.execute("ALTER TABLE shift RENAME TO temporarily_unavailable_shift");
            try {
                var failure = get(client);
                assertThat(failure.statusCode()).isEqualTo(500);
                assertThat(failure.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
                var problem = JsonMapper.builder().build().readTree(failure.body());
                assertThat(problem.get("status").asInt()).isEqualTo(500);
                assertThat(problem.get("detail").asString()).isEqualTo("Unable to load data. Please try again later.");
                assertThat(failure.body()).doesNotContain("SELECT", "temporarily_unavailable_shift", "jdbc:");
            } finally {
                jdbc.execute("ALTER TABLE temporarily_unavailable_shift RENAME TO shift");
            }
        }
    }

    private HttpResponse<String> get(HttpClient client) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/shifts"))
            .GET().build(), HttpResponse.BodyHandlers.ofString());
    }
}
