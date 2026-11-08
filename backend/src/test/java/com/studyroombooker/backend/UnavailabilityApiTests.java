package com.studyroombooker.backend;

import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import com.studyroombooker.backend.auth.MagicLinkTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(UnavailabilityApiTests.FixedTime.class)
class UnavailabilityApiTests extends PostgresIntegrationTest {
    @TestConfiguration
    static class FixedTime {
        // Thursday, November 5, 2026, 6:00 AM in Chicago.
        @Bean @Primary Clock testClock() {
            return Clock.fixed(Instant.parse("2026-11-05T12:00:00Z"), ZoneId.of("America/Chicago"));
        }
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired MagicLinkTokenService tokens;
    @Value("${local.server.port}") int port;
    TestHttp one;

    @BeforeEach
    void seed() throws Exception {
        jdbc.execute("TRUNCATE shift, study_block_backup_room, study_block, member_unavailability, member, room, library_hours RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO member(name, email) VALUES ('One', 'one@example.test'), ('Two', 'two@example.test')");
        jdbc.update("INSERT INTO room(code, name) VALUES ('A', 'Room A')");
        jdbc.update("INSERT INTO library_hours SELECT day, time '00:00', time '24:00' FROM generate_series(1, 7) AS day");
        one = TestHttp.signedIn(port, tokens, "one@example.test");
    }

    @Test
    void createsListsAndDeletesOneTimeAndWeeklyEntries() throws Exception {
        var oneTime = one.postJson("/api/me/unavailability",
            "{\"kind\":\"ONE_TIME\",\"date\":\"2026-11-10\",\"startTime\":\"09:00\",\"endTime\":\"12:30\"}");
        assertThat(oneTime.statusCode()).as(oneTime.body()).isEqualTo(201);
        assertThat(json(oneTime).get("startTime").asString()).isEqualTo("09:00");
        assertThat(json(oneTime).get("weekday").isNull()).isTrue();

        var weekly = one.postJson("/api/me/unavailability",
            "{\"kind\":\"WEEKLY\",\"weekday\":2,\"startTime\":\"18:00\",\"endTime\":\"24:00\",\"validUntil\":\"2026-12-15\"}");
        assertThat(weekly.statusCode()).as(weekly.body()).isEqualTo(201);
        assertThat(json(weekly).get("validFrom").asString()).isEqualTo("2026-11-05");
        assertThat(json(weekly).get("endTime").asString()).isEqualTo("24:00");

        var list = json(one.get("/api/me/unavailability"));
        assertThat(list.size()).isEqualTo(2);
        assertThat(list.get(0).get("kind").asString()).isEqualTo("ONE_TIME");
        assertThat(list.get(1).get("kind").asString()).isEqualTo("WEEKLY");

        long id = json(oneTime).get("id").asLong();
        assertThat(delete(one, id).statusCode()).isEqualTo(204);
        assertThat(json(one.get("/api/me/unavailability")).size()).isEqualTo(1);
        assertThat(delete(one, id).statusCode()).isEqualTo(404);
    }

    @Test
    void membersSeeAndDeleteOnlyTheirOwnEntries() throws Exception {
        long id = json(one.postJson("/api/me/unavailability",
            "{\"kind\":\"ONE_TIME\",\"date\":\"2026-11-10\",\"startTime\":\"09:00\",\"endTime\":\"10:00\"}")).get("id").asLong();
        var two = TestHttp.signedIn(port, tokens, "two@example.test");
        assertThat(json(two.get("/api/me/unavailability")).size()).isZero();
        assertThat(delete(two, id).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_unavailability", Integer.class)).isEqualTo(1);
        assertThat(new TestHttp(port).get("/api/me/unavailability").statusCode()).isEqualTo(401);
    }

    @Test
    void rejectsInvalidEntries() throws Exception {
        String[] invalid = {
            "{\"kind\":\"ONE_TIME\",\"date\":\"2026-11-04\",\"startTime\":\"09:00\",\"endTime\":\"10:00\"}",
            "{\"kind\":\"ONE_TIME\",\"startTime\":\"09:00\",\"endTime\":\"10:00\"}",
            "{\"kind\":\"ONE_TIME\",\"date\":\"2026-11-10\",\"startTime\":\"10:00\",\"endTime\":\"09:00\"}",
            "{\"kind\":\"ONE_TIME\",\"date\":\"2026-11-10\",\"weekday\":2,\"startTime\":\"09:00\",\"endTime\":\"10:00\"}",
            "{\"kind\":\"WEEKLY\",\"weekday\":8,\"startTime\":\"09:00\",\"endTime\":\"10:00\"}",
            "{\"kind\":\"WEEKLY\",\"weekday\":2,\"startTime\":\"09:00\",\"endTime\":\"10:00\",\"validFrom\":\"2026-12-01\",\"validUntil\":\"2026-11-30\"}",
            "{\"kind\":\"WEEKLY\",\"weekday\":2,\"startTime\":\"9am\",\"endTime\":\"10:00\"}",
            "{\"kind\":\"MONTHLY\",\"startTime\":\"09:00\",\"endTime\":\"10:00\"}",
        };
        for (String body : invalid) {
            var response = one.postJson("/api/me/unavailability", body);
            assertThat(response.statusCode()).as(body).isEqualTo(400);
            assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM member_unavailability", Integer.class)).isZero();
    }

    @Test
    void newUnavailabilityIsHonoredByTheNextAssignment() throws Exception {
        // One would normally win the tie (lowest ID); a Tuesday evening rule hands it to Two.
        one.postJson("/api/me/unavailability", "{\"kind\":\"WEEKLY\",\"weekday\":2,\"startTime\":\"17:00\",\"endTime\":\"24:00\"}");
        var created = one.postJson("/api/study-blocks",
            "{\"date\":\"2026-11-10\",\"startTime\":\"18:00\",\"endTime\":\"20:00\",\"preferredRoomId\":1}");
        assertThat(created.statusCode()).as(created.body()).isEqualTo(201);
        assertThat(jdbc.queryForObject("SELECT m.name FROM shift s JOIN member m ON m.id = s.member_id", String.class))
            .isEqualTo("Two");
    }

    private HttpResponse<String> delete(TestHttp http, long id) throws Exception {
        return http.send(http.request("/api/me/unavailability/" + id).header("X-XSRF-TOKEN", http.csrf()).DELETE());
    }

    private static JsonNode json(HttpResponse<String> response) {
        return JsonMapper.builder().build().readTree(response.body());
    }
}
