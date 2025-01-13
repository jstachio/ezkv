package io.jstach.ezkv.kvs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.stream.Collectors;

import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;

import io.jstach.ezkv.kvs.KeyValuesEnvironment.Logger;
import io.jstach.ezkv.kvs.KeyValuesServiceProvider.KeyValuesProvider;

class KeyValuesSystemTest {

	static PrintStream out = Objects.requireNonNull(System.out);

	record MyProvider() implements KeyValuesProvider {

		@Override
		public void provide(ProviderContext context, KeyValues.Builder builder) {
			builder.add("ref1", "refValue");
		}

	}

	/*
	 * Yes it is ridiculous how this is all one test at the moment. It is essentially a
	 * smoke test and not a unit test.
	 */
	@Test
	void testLoader() throws FileNotFoundException, IOException {
		Properties properties = new Properties();
		properties.setProperty("user.home", "/home/kenny");
		String cwd = Path.of(".").normalize().toAbsolutePath().toString();

		var logger = new TestLogger();

		String stdin = """
				stdin_password=guest
				""";

		var environment = new KeyValuesEnvironment() {
			@Override
			public Properties getSystemProperties() {
				return properties;
			}

			@Override
			public Logger getLogger() {
				return logger;
			}

			@Override
			public InputStream getStandardInput() {
				return new ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8));
			}

			@Override
			public @NonNull String[] getMainArgs() {
				return new String[] { "--passwords" };
			}

		};
		var system = KeyValuesResource.builder(URI.create("system:///"))
			.name("system")
			.noInterpolation(true)
			._addFlag(LoadFlag.NO_ADD) // For SentryMan :)
			.build();

		{
			var b = KeyValues.builder();
			DefaultKeyValuesResourceParser.of().formatResource(system, b::add);
			String actual = b.build().toString();
			String expected = """
					KeyValues[
					_load_system=system\\:///
					_flags_system=NO_ADD,NO_INTERPOLATE
					]
					""";
			assertEquals(expected, actual);
		}

		// TODO Checker and Eclipse conflict here and both have bugs
		// Eclipse needs the witness to infer nonnull
		// Checker doesn't like the witness not having KeyFor which this library
		// does not care bout and to be honest is a bug with checker
		// more so than eclipse.
		var map = Map.of("fromMap2", "2", "fromMap1", "1");

		@SuppressWarnings("null")
		var kvs = KeyValuesSystem.builder()
			.environment(environment)
			.provider(new MyProvider()) //
			.build() //
			.loader() //
			.add("provider:///")
			.add(system)
			.add("classpath:/test-props/testLoader.properties")
			.add("classpaths:/classpathstar.properties")
			.add("stdin:///?_p_stdin_arg=--passwords&_mime=properties&_flag=sensitive")
			.add("extra", KeyValues.builder().add(map.entrySet()).build())
			.load();

		out.println(kvs);
		{
			String actual = kvs.toString();
			String expected = """
					KeyValues[
					ref1=refValue
					stuff=/home/kenny
					blah=/home/kenny
					message=/home/kenny hello
					profile1=loaded
					profile1=loaded 2
					profile2=loaded
					me=found
					sensitive_a=REDACTED
					sensitive_ab=REDACTED
					mypassword=REDACTED
					classpathstar=ezkv
					stdin_password=REDACTED
					fromMap1=1
					fromMap2=2
					]
					""";
			assertEquals(expected, actual);
		}

		{
			String actual = KeyValuesMedia.ofProperties().formatter().format(kvs);
			String expected = """
					ref1=refValue
					stuff=/home/kenny
					blah=/home/kenny
					message=/home/kenny hello
					profile1=loaded
					profile1=loaded 2
					profile2=loaded
					me=found
					sensitive_a=a
					sensitive_ab=ab
					mypassword=1.2.3.4.5
					classpathstar=ezkv
					stdin_password=guest
					fromMap1=1
					fromMap2=2
									""";
			assertEquals(expected, actual);
		}

		{
			KeyValuesMedia.Formatter formatter = (a, _kvs) -> {
				for (var kv : _kvs) {
					a.append(kv.toString()).append("\n");
				}
			};

			String actual = formatter.format(kvs);
			String expected = """
					KeyValue[key='ref1', raw='refValue', expanded='refValue', source=Source[uri=provider:///MyProvider, reference=[key='_load_MyProvider0', in='provider:///'], index=1]]
					KeyValue[key='stuff', raw='${user.home}', expanded='/home/kenny', source=Source[uri=classpath:/test-props/testLoader.properties, index=1]]
					KeyValue[key='blah', raw='${MISSING:-${stuff}}', expanded='/home/kenny', source=Source[uri=classpath:/test-props/testLoader.properties, index=2]]
					KeyValue[key='message', raw='${stuff} hello', expanded='/home/kenny hello', source=Source[uri=classpath:/test-props/testLoader-child.properties, reference=[key='_load_child', in='classpath:/test-props/testLoader.properties'], index=1]]
					KeyValue[key='profile1', raw='loaded', expanded='loaded', source=Source[uri=classpath:/test-props/testLoader-profile1.properties, reference=[key='_load_profiles0', in='profile.classpath:/test-props/testLoader-__PROFILE__.properties'], index=1]]
					KeyValue[key='profile1', raw='loaded 2', expanded='loaded 2', source=Source[uri=classpath:/test-props/testLoader-profile2.properties, reference=[key='_load_profiles1', in='profile.classpath:/test-props/testLoader-__PROFILE__.properties'], index=1]]
					KeyValue[key='profile2', raw='loaded', expanded='loaded', source=Source[uri=classpath:/test-props/testLoader-profile2.properties, reference=[key='_load_profiles1', in='profile.classpath:/test-props/testLoader-__PROFILE__.properties'], index=2]]
					KeyValue[key='me', originalKey='matchme', raw='found', expanded='found', source=Source[uri=classpath:/test-props/testLoader-filter.properties, reference=[key='_load_filter', in='classpath:/test-props/testLoader-child.properties'], index=1]]
					KeyValue[key='sensitive_a', raw='REDACTED', expanded='REDACTED', source=Source[uri=classpath:/test-props/testLoader-sensitive.properties, reference=[key='_load_luggage', in='classpath:/test-props/testLoader.properties'], index=1]]
					KeyValue[key='sensitive_ab', raw='REDACTED', expanded='REDACTED', source=Source[uri=classpath:/test-props/testLoader-sensitive.properties, reference=[key='_load_luggage', in='classpath:/test-props/testLoader.properties'], index=2]]
					KeyValue[key='mypassword', raw='REDACTED', expanded='REDACTED', source=Source[uri=classpath:/test-props/testLoader-sensitive.properties, reference=[key='_load_luggage', in='classpath:/test-props/testLoader.properties'], index=3]]
					KeyValue[key='classpathstar', raw='ezkv', expanded='ezkv', source=Source[uri=file:{{CWD}}/target/test-classes/classpathstar.properties, reference=[key='_load_root30', in='classpaths:/classpathstar.properties'], index=1]]
					KeyValue[key='stdin_password', raw='REDACTED', expanded='REDACTED', source=Source[uri=stdin:///, index=1]]
					KeyValue[key='fromMap1', raw='1', expanded='1', source=Source[uri=null:///extra, index=0]]
					KeyValue[key='fromMap2', raw='2', expanded='2', source=Source[uri=null:///extra, index=0]]
					"""
				.replace("{{CWD}}", cwd);
			assertEquals(expected, actual);
		}

		{

			String actual = logger.toString();
			String expected = """
					[DEBUG] Loading uri='provider:///'
					[INFO ] Loaded  uri='provider:///'
					[DEBUG] Loading uri='provider:///MyProvider' specified with key: '_load_MyProvider0' in uri='provider:///'
					[INFO ] Loaded  uri='provider:///MyProvider'
					[DEBUG] Loading uri='system:///' flags=[NO_ADD, NO_INTERPOLATE]
					[INFO ] Loaded  uri='system:///' flags=[NO_ADD, NO_INTERPOLATE]
					[DEBUG] Loading uri='classpath:/test-props/testLoader.properties'
					[INFO ] Loaded  uri='classpath:/test-props/testLoader.properties'
					[DEBUG] Loading uri='classpath:/test-props/testLoader-child.properties' flags=[NO_REQUIRE] specified with key: '_load_child' in uri='classpath:/test-props/testLoader.properties'
					[INFO ] Loaded  uri='classpath:/test-props/testLoader-child.properties' flags=[NO_REQUIRE]
					[DEBUG] Loading uri='profile.classpath:/test-props/testLoader-__PROFILE__.properties' specified with key: '_load_profiles' in uri='classpath:/test-props/testLoader-child.properties'
					[INFO ] Found profiles: [profile1, profile2]
					[INFO ] Loaded  uri='profile.classpath:/test-props/testLoader-__PROFILE__.properties'
					[DEBUG] Loading uri='classpath:/test-props/testLoader-profile1.properties' specified with key: '_load_profiles0' in uri='profile.classpath:/test-props/testLoader-__PROFILE__.properties'
					[INFO ] Loaded  uri='classpath:/test-props/testLoader-profile1.properties'
					[DEBUG] Loading uri='classpath:/test-props/testLoader-profile2.properties' specified with key: '_load_profiles1' in uri='profile.classpath:/test-props/testLoader-__PROFILE__.properties'
					[INFO ] Loaded  uri='classpath:/test-props/testLoader-profile2.properties'
					[DEBUG] Loading uri='classpath:/test-props/testLoader-filter.properties' specified with key: '_load_filter' in uri='classpath:/test-props/testLoader-child.properties'
					[INFO ] Loaded  uri='classpath:/test-props/testLoader-filter.properties'
					[DEBUG] Loading uri='classpath:/test-props/testLoader-doesnotexist.properties' flags=[NO_REQUIRE] specified with key: '_load_noexist' in uri='classpath:/test-props/testLoader.properties'
					[DEBUG] Missing uri='classpath:/test-props/testLoader-doesnotexist.properties' flags=[NO_REQUIRE]
					[DEBUG] Loading uri='classpath:/test-props/testLoader-childwarn.properties' flags=[NO_LOAD_CHILDREN] specified with key: '_load_childwarn' in uri='classpath:/test-props/testLoader.properties'
					[INFO ] Loaded  uri='classpath:/test-props/testLoader-childwarn.properties' flags=[NO_LOAD_CHILDREN]
					[WARN ] Resource is not allowed to load children but had load keys (ignoring). resource: uri='classpath:/test-props/testLoader-childwarn.properties' flags=[NO_LOAD_CHILDREN]
						<-- specified with key: '_load_childwarn' in uri='classpath:/test-props/testLoader.properties'
					[DEBUG] Loading uri='classpath:/test-props/testLoader-sensitive.properties' flags=[SENSITIVE] specified with key: '_load_luggage' in uri='classpath:/test-props/testLoader.properties'
					[INFO ] Loaded  uri='classpath:/test-props/testLoader-sensitive.properties' flags=[SENSITIVE]
					[DEBUG] Loading uri='classpaths:/classpathstar.properties'
					[INFO ] Loaded  uri='classpaths:/classpathstar.properties'
					[DEBUG] Loading uri='file:{{CWD}}/target/test-classes/classpathstar.properties' flags=[NO_LOAD_CHILDREN] specified with key: '_load_root30' in uri='classpaths:/classpathstar.properties'
					[INFO ] Loaded  uri='file:{{CWD}}/target/test-classes/classpathstar.properties' flags=[NO_LOAD_CHILDREN]
					[DEBUG] Loading uri='stdin:///' flags=[SENSITIVE]
					[INFO ] Loaded  uri='stdin:///' flags=[SENSITIVE]
					"""
				.replace("{{CWD}}", cwd);
			assertEquals(expected, actual);
		}
	}

	@Test
	void testFailure() throws Exception {

		var logger = new TestLogger();
		var environment = new KeyValuesEnvironment() {
			@Override
			public Logger getLogger() {
				return logger;
			}
		};

		try {
			KeyValuesSystem.builder()
				.environment(environment)
				.build() //
				.loader() //
				.add("classpath:/test-props/testFailure.properties")
				.load();
			fail();
		}
		catch (IOException e) {
			String expected = """
					Resource not found. resource: uri='classpath:/test-props/testLoader-doesnotexist.properties'
						<-- specified with key: '_load_noexist' in uri='classpath:/test-props/testFailure.properties'""";
			String actual = e.getMessage();
			assertEquals(expected, actual);
		}
	}

	@Test
	void testKeyValuesResourceToString() throws KeyValuesResourceParserException {
		var r = KeyValuesResource.builder(URI.create("system:///?_filter_sed=s/a/b/"))
			.name("system")
			.noInterpolation(true)
			.noAdd(true)
			.sensitive(true)
			.parameter("custom", "something")
			.build(DefaultKeyValuesResourceParser.DEFAULT);
		String actual = r.toString();
		String expected = """
				DefaultKeyValuesResource[uri=system:///, name=system, loadFlags=[NO_ADD, NO_INTERPOLATE, SENSITIVE], reference=null, mediaType=null, parameters=MapParameters[map={custom=something}], filters=[Filter[filter=sed, expression=s/a/b/, name=]], normalized=true]
						"""
			.trim();
		assertEquals(expected, actual);
	}

	// @Test
	// void testManifest() throws IOException, URISyntaxException {
	// var urls =
	// Collections.list(KeyValuesSystem.class.getClassLoader().getResources("META-INF/MANIFEST.MF"));
	// for( var url : urls) {
	// out.println("url: " + url.toURI());
	// }
	// }

	static class TestLogger implements Logger {

		final List<String> events = new ArrayList<>();

		public TestLogger() {
			super();
		}

		// public void clear() {
		// events.clear();
		// }
		//
		// public List<String> getEvents() {
		// return events;
		// }

		void add(String m) {
			events.add(m);
			out.println(m);

		}

		@Override
		public void debug(String message) {
			add("[DEBUG] " + message);
		}

		@Override
		public void info(String message) {
			add("[INFO ] " + message);
		}

		@Override
		public void warn(String message) {
			add("[WARN ] " + message);
		}

		@Override
		public String toString() {
			return events.stream().map(line -> line + "\n").collect(Collectors.joining());
		}

	}

}
