package io.jstach.ezkv.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

class EzkvConfigTest {

	@Test
	void testOf() {
		var m = Map.of("logging.systemlogger.initialize", "true", "logging.systemlogger.intialize", "true",
				"logging.level.io.jstach.ezkv", "DEBUG");
		EzkvConfig.setDefaultProperties(m);
		var config = EzkvConfig.of();
		// config.reload();

		assertEquals("Hello", config.getProperty("DEMO"));

	}

}
