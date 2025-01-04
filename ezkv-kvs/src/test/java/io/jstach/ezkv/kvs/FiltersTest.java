package io.jstach.ezkv.kvs;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.function.Predicate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.jstach.ezkv.kvs.KeyValuesServiceProvider.KeyValuesFilter.Filter;
import io.jstach.ezkv.kvs.KeyValuesServiceProvider.KeyValuesFilter.FilterContext;
import io.jstach.ezkv.kvs.Variables.Parameters;

class FiltersTest {

	@Test
	void testJoin() {
		String properties = """
				a=1
				b=2
				a=3
				""";
		var kvs = KeyValuesMedia.ofProperties().parser().parse(properties);
		String filterName = "join";
		String expression = ",";

		var result = runFilter(kvs, filterName, expression);

		String actual = result.toString();
		String expected = """
				KeyValues[
				a=1,3
				b=2
				]
				""";
		assertEquals(expected, actual);
	}

	@ParameterizedTest
	@ValueSource(strings = { "val", "value" })
	void testSedValue(String target) {
		String properties = """
				a=1
				b=2
				a=3
				""";
		var kvs = KeyValuesMedia.ofProperties().parser().parse(properties);
		String filterName = "sed_" + target;
		String expression = "s/[12]/small/";

		var result = runFilter(kvs, filterName, expression);

		String actual = result.toString();
		String expected = """
				KeyValues[
				a=small
				b=small
				a=3
				]
				""";
		assertEquals(expected, actual);
	}

	private KeyValues runFilter(KeyValues kvs, String filterName, String expression) {
		Filter filter = new Filter(filterName, expression, "blah");
		var s = KeyValuesSystem.builder().build();
		Predicate<KeyValue> ignore = kv -> false;
		FilterContext context = new FilterContext(s.environment(), Parameters.of(Map.of()), ignore);
		var result = s.filter().filter(context, kvs, filter).orElseThrow();
		return result;
	}

}
