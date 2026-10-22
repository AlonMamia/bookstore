package com.alon.bookstore;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		// Pinned to match the deployment target (infra/variables.tf: rds_engine_version
		// = "16.4") instead of floating on "latest", which can silently drift to a
		// version the app was never actually tested against.
		return new PostgreSQLContainer(DockerImageName.parse("postgres:16-alpine"));
	}

}
