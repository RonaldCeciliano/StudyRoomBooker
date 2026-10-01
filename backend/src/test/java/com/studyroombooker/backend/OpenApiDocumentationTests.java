package com.studyroombooker.backend;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Value;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OpenApiDocumentationTests extends PostgresIntegrationTest {

	@Value("${local.server.port}")
	private int port;

	@Test
	void servesCommittedContractAndConfiguresSwaggerWithoutImplementingShifts() throws Exception {
		try (var client = HttpClient.newHttpClient()) {
			var contract = get(client, "/openapi/studyroombooker.yaml");
			assertThat(contract.statusCode()).isEqualTo(200);
			assertThat(contract.body()).isEqualTo(Files.readString(
					Path.of("../docs/openapi/studyroombooker.yaml")));
			var config = get(client, "/v3/api-docs/swagger-config");
			assertThat(config.statusCode()).isEqualTo(200);
			assertThat(config.body()).contains("/openapi/studyroombooker.yaml");
			assertThat(get(client, "/swagger-ui/index.html").statusCode()).isEqualTo(200);
			assertThat(get(client, "/api/shifts").statusCode()).isEqualTo(404);
		}
	}

	private HttpResponse<String> get(HttpClient client, String path) throws Exception {
		return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.GET().build(), HttpResponse.BodyHandlers.ofString());
	}
}
