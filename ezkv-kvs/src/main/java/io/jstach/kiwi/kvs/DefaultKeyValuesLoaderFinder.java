package io.jstach.kiwi.kvs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import io.jstach.kiwi.kvs.KeyValuesEnvironment.ResourceStreamLoader;
import io.jstach.kiwi.kvs.KeyValuesMedia.Parser;
import io.jstach.kiwi.kvs.KeyValuesServiceProvider.KeyValuesLoaderFinder;

enum DefaultKeyValuesLoaderFinder implements KeyValuesLoaderFinder {

	CLASSPATH {
		@Override
		protected KeyValues load(LoaderContext context, KeyValuesResource resource) throws IOException {
			var parser = context.requireParser(resource);
			return load(context, resource, parser);
		}
	},
	FILE {
		@Override
		protected KeyValues load(LoaderContext context, KeyValuesResource resource) throws IOException {
			var parser = context.requireParser(resource);
			return load(context, resource, parser);
		}

		@Override
		boolean matches(KeyValuesResource resource) {
			return filePathOrNull(resource.uri()) != null;
		}
	},
	SYSTEM {
		@Override
		protected KeyValues load(LoaderContext context, KeyValuesResource resource) throws IOException {
			var builder = KeyValues.builder(resource);
			var properties = context.environment().getSystemProperties();
			for (var sp : properties.stringPropertyNames()) {
				String value = properties.getProperty(sp);
				if (value != null) {
					builder.add(sp, value);
				}
			}
			return builder.build();
		}

	},
	PROFILE {
		private final static String SCHEME = "profile";

		@Override
		protected KeyValues load(LoaderContext context, KeyValuesResource resource) throws IOException {
			return profiles(SCHEME, context, resource);
		}

		@Override
		boolean matches(KeyValuesResource resource) {
			String scheme = resource.uri().getScheme();
			if (scheme == null) {
				return false;
			}
			return scheme.startsWith(SCHEME + ".");
		}
	},
	NULL {
		@Override
		protected KeyValues load(LoaderContext context, KeyValuesResource resource) throws IOException {
			throw new RuntimeException("null resource not allowed. " + resource);
		}

	},
	CMD {
		@Override
		protected KeyValues load(LoaderContext context, KeyValuesResource resource) throws IOException {
			var b = KeyValues.builder(resource);
			List<String> args = Arrays.asList(context.environment().getMainArgs());
			propertiesFromCommandLine(args, b::add);
			var kvs = b.build();
			return maybeUseKeyFromUri(context, resource, kvs);

		}

		static void propertiesFromCommandLine(Iterable<String> args, BiConsumer<String, String> consumer) {
			for (String arg : args) {
				String[] kv = arg.split("=", 2);
				if (kv.length < 2)
					continue;
				String key = kv[0];
				String value = kv[1];
				consumer.accept(key, value);
			}
		}
	},
	ENV {
		@Override
		protected KeyValues load(LoaderContext context, KeyValuesResource resource) throws IOException {
			var b = KeyValues.builder(resource);
			var env = context.environment().getSystemEnv();
			env.entrySet().forEach(b::add);
			var kvs = b.build();
			return maybeUseKeyFromUri(context, resource, kvs);
		}
	},

	;

	@Override
	public Optional<KeyValuesLoader> findLoader(LoaderContext context, KeyValuesResource resource) {
		if (!matches(resource)) {
			return Optional.empty();
		}
		KeyValuesLoader loader = loader(context, resource);
		return Optional.of(loader);
	}

	protected KeyValuesLoader loader(LoaderContext context, KeyValuesResource resource) {
		KeyValuesLoader loader = () -> {
			return load(context, resource);
		};
		return loader;
	}

	protected abstract KeyValues load(LoaderContext context, KeyValuesResource resource) throws IOException;

	protected KeyValues load(LoaderContext context, KeyValuesResource resource, Parser parser) throws IOException {
		var is = openURI(resource.uri(), context.environment().getResourceStreamLoader());
		return parser.parse(resource, is);
	}

	boolean matches(KeyValuesResource resource) {
		return name().toLowerCase(Locale.ROOT).equals(resource.uri().getScheme());
	}

	static KeyValues profiles(String scheme, LoaderContext context, KeyValuesResource resource) throws IOException {
		String uriString = resource.uri().toString();
		uriString = uriString.substring(scheme.length() + 1);
		// var uri = URI.create(uriString);
		var logger = context.environment().getLogger();
		var vars = Variables.builder()
			.add(resource.parameters())
			.add(context.variables().renameKey(k -> "_" + k))
			.build();
		var profile = vars.findEntry("profile", "profile.active", "profile.default")
			.map(e -> e.getValue())
			.orElse(null);
		if (profile == null) {
			String error = "profile parameter is required. Set it to CSV list of profiles.";
			logger.info("Profile(s) could not be found for resource. resource: " + resource.description()
					+ " tried parameter: " + context.formatParameterKey(resource, "profile"));
			// TODO custom exception for missing parameters ?
			throw new FileNotFoundException(error);
		}
		if (!uriString.contains("__PROFILE__")) {
			throw new IOException(
					"Resource needs '__PROFILE__' in URI to be replaced by extracted profiles. URI: " + uriString);
		}
		List<String> profiles = Stream.of(profile.split(",")).filter(p -> !p.isBlank()).distinct().toList();

		logger.info("Found profiles: " + profiles);
		var builder = KeyValues.builder(resource);

		int i = 0;
		for (var p : profiles) {
			var value = uriString.replace("__PROFILE__", p);
			var b = resource.toBuilder();
			b.uri(URI.create(value));
			b.name(resource.name() + i++);
			var profileResource = b.build();
			context.formatResource(profileResource, builder::add);
		}
		var kvs = builder.build();
		return kvs;
	}

	static @Nullable Path filePathOrNull(URI u) {
		if ("file".equals(u.getScheme())) {
			return Path.of(u);
		}
		else if (u.getScheme() == null && u.getPath() != null) {
			return Path.of(u.getPath());
		}
		return null;
	}

	static String normalizePath(URI u) {
		String p = u.getPath();
		if (p == null || p.equals("/")) {
			p = "";
		}
		else if (p.startsWith("/")) {
			p = p.substring(1);
		}
		return p;
	}

	static InputStream openURI(URI u, ResourceStreamLoader loader) throws IOException {
		if ("classpath".equals(u.getScheme())) {
			String path = u.getPath();
			if (path == null) {
				throw new MalformedURLException("Classpath scheme URI requires a path. URI: " + u);
			}
			else if (path.startsWith("/")) {
				path = path.substring(1);
			}

			InputStream stream = loader.getResourceAsStream(path);
			if (stream == null) {
				throw new FileNotFoundException("Classpath URI: " + u + " cannot be found with loader: " + loader);
			}
			return stream;
		}

		var path = filePathOrNull(u);
		if (path != null) {
			return Files.newInputStream(path);
		}
		throw new FileNotFoundException("URI is not classpath or file. URI: " + u);
	}

	private static KeyValues maybeUseKeyFromUri(LoaderContext context, KeyValuesResource resource, KeyValues kvs)
			throws FileNotFoundException {
		String path = normalizePath(resource.uri());
		if (path.isBlank()) {
			return kvs;
		}
		/*
		 * If the path is not blank then a specific key is being requested that contains
		 * encoded key values.
		 *
		 * A use case might be some key value that contains JSON.
		 */
		var logger = context.environment().getLogger();
		logger.debug("Using key specified in URI path. key: " + path + " resource: " + resource);

		var kvResource = kvs.filter(kv -> kv.key().equals(path)).last().orElse(null);
		if (kvResource == null) {
			throw new FileNotFoundException(
					"Key not found specified in URI path. key: '" + path + "' resource: " + resource);
		}
		var parser = context.requireParser(resource);
		return parser.parse(kvResource.value());
	}

	record FilterParameters(String prefix, String prefixReplacement) {
		private static final FilterParameters NONE = new FilterParameters("", "");

		static FilterParameters none() {
			return NONE;
		}

		static FilterParameters parse(URI uri) {
			List<String> paths = pathArgs(uri.getPath());

			String prefix = "";
			String prefixReplacement = "";
			if (paths.size() > 0) {
				prefix = paths.get(0);
			}
			if (paths.size() > 1) {
				prefixReplacement = paths.get(1);
			}
			return new FilterParameters(prefix, prefixReplacement);
		}

		boolean isNone() {
			return none().equals(this);
		}

		@Nullable
		String transformOrNull(String key) {
			if (!key.startsWith(prefix)) {
				return null;
			}
			return prefixReplacement + removeStart(key, prefix);
		}

		private static String removeStart(final String str, final String remove) {
			if (str.startsWith(remove)) {
				return str.substring(remove.length());
			}
			return str;
		}

		static List<String> pathArgs(@Nullable String p) {
			if (p == null || p.equals("/")) {
				p = "";
			}
			else if (p.startsWith("/")) {
				p = p.substring(1);
			}
			String[] paths = p.split("/");
			return List.of(paths);
		}

		// @Override
		public KeyValues apply(KeyValue keyValue) {
			String newKey = transformOrNull(keyValue.key());
			if (newKey == null) {
				return KeyValues.empty();
			}
			return KeyValues.of(keyValue.withKey(newKey));
		}

	}

}