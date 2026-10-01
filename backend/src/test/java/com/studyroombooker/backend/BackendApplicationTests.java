package com.studyroombooker.backend;

import java.time.Clock;
import java.time.ZoneId;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

class BackendApplicationTests extends PostgresIntegrationTest {

	@Autowired
	private Clock clock;

	@Autowired
	private DataSource dataSource;

	@Test
	void contextLoads() throws Exception {
		assertThat(clock.getZone()).isEqualTo(ZoneId.of("America/Chicago"));
		try (var connection = dataSource.getConnection();
				var statement = connection.createStatement();
				var result = statement.executeQuery("SELECT current_setting('TimeZone'), 1")) {
			assertThat(result.next()).isTrue();
			assertThat(result.getString(1)).isEqualTo("America/Chicago");
			assertThat(result.getInt(2)).isEqualTo(1);
		}
	}
}
