package com.alon.bookstore;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

// Boots the real Spring context against a Testcontainers Postgres instance, so it
// belongs in the Failsafe-run IT suite, not Surefire's *Test.java unit suite - see
// .claude/rules/testing.md. It was previously misnamed "...Tests.java", which Surefire's
// default include pattern (**/*Tests.java, not just **/*Test.java) picks up too, making
// a Docker-dependent test silently run where "no Docker required" was assumed.
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class BookstoreApplicationIT {

	@Test
	void contextLoads() {
	}

}
