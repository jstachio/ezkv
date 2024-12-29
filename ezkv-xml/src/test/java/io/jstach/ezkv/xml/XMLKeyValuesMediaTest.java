package io.jstach.ezkv.xml;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class XMLKeyValuesMediaTest {

	@Test
	void testParser() {
		String xml = """
				<a>
					<b>1</b>
					<b>2.0<c>3</c></b>
					<d>value</d>
				</a>
				""";
		var parser = new XMLKeyValuesMedia().parser();
		var kvs = parser.parse(xml);
		String actual = kvs.toString();
		String expected = """
				KeyValues[
				a.b=1
				a.b=2.0
				a.b.c=3
				a.d=value
				]
				""";
		assertEquals(expected, actual);
	}

}
