package com.mmea.albumy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

/** Integration smoke test: needs MySQL + Redis, so it only runs when DB_URL is set (CI does). */
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@SpringBootTest(properties = "jwt.secret=test-only-secret-0123456789abcdef0123456789abcdef")
class AlbumyApplicationTests {

	@Test
	void contextLoads() {
	}

}