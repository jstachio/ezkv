package io.jstach.ezkv.xml;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junitpioneer.jupiter.cartesian.CartesianTest;

import io.jstach.ezkv.kvs.KeyValues;
import io.jstach.ezkv.kvs.KeyValuesMedia;
import io.jstach.ezkv.kvs.KeyValuesSystem;
import io.jstach.ezkv.kvs.Variables;

class XMLKeyValuesMediaTest {

	String xml = """
			<a>
				<b>1</b>
				<b>2.0<c>3</c>
				</b>
				<d>value</d>
				<e f="attr" />
			</a>
			""";

	@Test
	void testParser() {

		Variables variables = Variables.builder() //
			.add(XMLKeyValuesMedia.MODE_PARAM, XMLKeyValuesMedia.MODE_VALUE_XPATH) //
			.build();
		var parser = new XMLKeyValuesMedia().parser(variables);
		var kvs = parser.parse(xml);
		String actual = kvs.toString();
		String expected = """
				KeyValues[
				/a/b[1]=1
				/a/b[2]=2.0
				/a/b[2]/c=3
				/a/d=value
				/a/e/@f=attr
				]
				""";
		assertEquals(expected, actual);
	}

	@CartesianTest
	void testParameter(@CartesianTest.Enum XMLKeyValuesMedia.Parameter parameter)
			throws NoSuchFileException, FileNotFoundException, IOException {
		var builder = Variables.builder();
		Map<String, String> ps = new LinkedHashMap<>();
		String key = parameter.key;
		String value = "^";
		String expected = switch (parameter) {
			case PREFIX -> """
					KeyValues[
					^a.b=1
					^a.b=2.0
					^a.b.c=3
					^a.d=value
					^a.e.f=attr
					]
					""";
			case ARRAY -> {
				value = XMLKeyValuesMedia.ARRAY_KEY_VALUE_ARRAY;
				yield """
						KeyValues[
						a.b[0]=1
						a.b[1]=2.0
						a.b[1].c=3
						a.d=value
						a.e.f=attr
						]
						""";
			}
			case ATTRIBUTE -> """
					KeyValues[
					a.b=1
					a.b=2.0
					a.b.c=3
					a.d=value
					a.e.^f=attr
					]
					""";
			case INDEX_START -> {
				value = "2";
				ps.put(XMLKeyValuesMedia.ARRAY_KEY_PARAM, "array");
				yield """
						KeyValues[
						a.b[2]=1
						a.b[3]=2.0
						a.b[3].c=3
						a.d=value
						a.e.f=attr
						]
						""";
			}
			case MODE -> {
				value = XMLKeyValuesMedia.MODE_VALUE_XPATH;
				yield """
						KeyValues[
						/a/b[1]=1
						/a/b[2]=2.0
						/a/b[2]/c=3
						/a/d=value
						/a/e/@f=attr
						]
						""";
			}
			case SEPARATOR -> """
					KeyValues[
					a^b=1
					a^b=2.0
					a^b^c=3
					a^d=value
					a^e^f=attr
					]
					""";
		};
		ps.put(key, value);
		builder.add(ps);
		var parser = new XMLKeyValuesMedia().parser(builder.build());
		// System.out.println(parser);
		var actual = parser.parse(xml).toString();
		assertEquals(expected, actual);
		/*
		 * Now we build a URI with query parameters
		 */
		String q = KeyValues.builder()
			.add(ps.entrySet()) //
			.build() //
			.map(kv -> kv.withKey("_p_" + kv.key())) //
			.format(KeyValuesMedia.ofUrlEncoded());

		actual = KeyValuesSystem.defaults() //
			.loader() //
			.add("classpath:/some.xml?" + q)
			.load()
			.toString();
		assertEquals(expected, actual);
	}

	@CartesianTest
	void testInvalid(@CartesianTest.Enum(names = { "ARRAY", "MODE" }) XMLKeyValuesMedia.Parameter parameter) {
		String key = parameter.key;
		String value = "garbage";
		var builder = Variables.builder();
		builder.add(key, value);
		var e = assertThrows(IllegalArgumentException.class, () -> {
			new XMLKeyValuesMedia().parser(builder.build()).parse(xml).toString();
		});
		String expected = switch (parameter) {
			case ARRAY ->
				"Parameter is invalid. key='xml_arraykey', value='garbage. xml_arraykey is incorrect and should be either: 'duplicate' or 'array', value = 'garbage''";
			case MODE ->
				"Parameter is invalid. key='xml_mode', value='garbage. Mode is incorrect and should be either: 'properties' or 'xpath''";
			default -> fail();
		};
		String actual = e.getMessage();
		assertEquals(expected, actual);

	}

}
