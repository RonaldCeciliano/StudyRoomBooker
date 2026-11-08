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
	void servesCommittedContractAndConfiguresSwagger() throws Exception {
		try (var client = HttpClient.newHttpClient()) {
			var contract = get(client, "/openapi/studyroombooker.yaml");
			assertThat(contract.statusCode()).isEqualTo(200);
			assertThat(contract.body()).isEqualTo(Files.readString(
					Path.of("../docs/openapi/studyroombooker.yaml")));
			var config = get(client, "/v3/api-docs/swagger-config");
			assertThat(config.statusCode()).isEqualTo(200);
			assertThat(config.body()).contains("/openapi/studyroombooker.yaml");
			assertThat(get(client, "/swagger-ui/index.html").statusCode()).isEqualTo(200);
		}
	}

	private HttpResponse<String> get(HttpClient client, String path) throws Exception {
		return client.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
				.GET().build(), HttpResponse.BodyHandlers.ofString());
	}

	@Test
	void releaseCannotTrustCallerSuppliedIdentityHeadersOrBody() throws Exception {
		// A valid CSRF token but no session: forged identity headers/body must not authenticate.
		var http = new TestHttp(port);
		var response = http.send(http.request("/api/shifts/1/release?memberId=1")
			.header("X-Test-Email", "owner@example.test")
			.header("X-Member-Id", "1")
			.header("X-XSRF-TOKEN", http.csrf())
			.header("Content-Type", "application/json")
			.POST(HttpRequest.BodyPublishers.ofString("{\"memberId\":1}")));
		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.headers().firstValue("Content-Type").orElse("")).contains("application/problem+json");
	}
}
