package io.jstach.ezkv.kvs;

import java.io.FileNotFoundException;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

import io.jstach.ezkv.kvs.KeyValuesServiceProvider.KeyValuesMediaFinder;
import io.jstach.ezkv.kvs.KeyValuesServiceProvider.KeyValuesProvider;
import io.jstach.ezkv.kvs.KeyValuesServiceProvider.KeyValuesProvider.ProviderContext;
import io.jstach.ezkv.kvs.KeyValuesServiceProvider.KeyValuesLoaderFinder.LoaderContext;
import io.jstach.ezkv.kvs.Variables.Parameters;

/**
 * A service provider interface (SPI) for extending ezkv's capabilities to support
 * additional media types and URI patterns. Implementations of this interface can be
 * loaded using {@link java.util.ServiceLoader}.
 *
 * <p>
 * This SPI allows customization and extension of ezkv's loading mechanisms and media type
 * parsing.
 *
 * <p>
 * Note: This interface is sealed, and any sub-interface implementations must be
 * registered using {@code provides KeyValuesServiceProvider with ...} in the
 * {@code module-info.java} or as entries in the
 * {@code META-INF/services/io.jstach.ezkv.kvs.KeyValuesServiceProvider} file.
 * Sub-interfaces such as {@link KeyValuesLoaderFinder} and {@link KeyValuesMediaFinder}
 * should <strong>not</strong> be directly referenced in the service registration.
 *
 * <p>
 * Example use cases include adding support for custom media types or integrating
 * specialized URI loading logic.
 */
public sealed interface KeyValuesServiceProvider {

	/**
	 * Since the service providers follow a "resolver" or "finder" pattern order of the
	 * providers matters.
	 * <p>
	 * Higher values are interpreted as lower priority. As a consequence, the provider
	 * with the lowest value has the highest priority (analogous to Spring's Ordered
	 * interface).
	 * </p>
	 * Canonically the builtin components start at {@value #BUILTIN_ORDER_START} so if one
	 * is looking to override they should use a lower value.
	 * @return by default {@code 0}
	 * @see #BUILTIN_ORDER_START
	 */
	default int order() {
		return 0;
	}

	/**
	 * The start order of the builtin components. The start order is low enough that all
	 * the builtin components will be less than {@code 0} on purpose to avoid accidental
	 * override.
	 */
	public static int BUILTIN_ORDER_START = -127;

	/**
	 * This service provider is for other modules in your application to provide a
	 * reference configuration to be overriden and can be loaded using the
	 * {@value KeyValuesResource#SCHEMA_PROVIDER} URI scheme. This is akin to
	 * <a href="https://github.com/lightbend/config?tab=readme-ov-file#standard-behavior">
	 * Lightbend/Typesafe Config <code>reference.conf</code></a> but without a resource
	 * call for maximum portability with things like GraalVM native, Maven shade plugin,
	 * and modular applications where the config could be an enscapulated resource
	 * (properties file) which would require the module to do the loading and not EZKV.
	 * <p>
	 * If one would like to load configuration from say properties file the implementation
	 * will have to do that on its own but is free to use the out of box
	 * {@link KeyValuesMedia} to parse things like properties files.
	 * <p>
	 * If using a Java modules it is recommended that the lower modules that are
	 * contributing config use <code>requires static</code> to the EZKV module to avoid
	 * unnecessary coupling if all they are doing is just providing reference config.
	 * <p>
	 * <strong>NOTE:</strong> It is recommend to avoid making key values that need
	 * interpolation from external variables as the variables may not be present. However
	 * interpolation based on the local keys being added is fine and encouraged (for
	 * example constructing a JDBC URL from a default port that is also provided). The
	 * idea again is that this reference config that needs to be guaranteed to be there
	 * for defaults and possibly participate in downstream variable/interpolation usage.
	 * <strong> It is highly recommended that you do not initialize other parts of the
	 * module that would initialize logging or something that needs configuration!
	 * </strong> If you need to use logging use the EZKV logging:
	 * {@link KeyValuesEnvironment#getLogger()} which will by default not initialize a
	 * logging framework.
	 *
	 * <strong>Example</strong>
	 * {@snippet :
	 * public class DatabaseConfigProvider implements KeyValuesServiceProvider.KeyValuesProvider {
	 *
	 * 	public void provide(KeyValues.Builder builder, LoaderContext context) {
	 * 		builder.add("database.host", "localhost");
	 * 		builder.add("database.port", "5245");
	 * 		builder.add("database.schema", "mydb");
	 * 		builder.add("database.url", "jdbc:postgresql://${database.host}/${database.port}/${database.schema}");
	 * 	}
	 *
	 * }
	 * }
	 *
	 * Down stream <code>database.port</code> could be overriden.
	 * <p>
	 * Another common usage is some module has an {@link Enum} of configuration options.
	 * This extension point allows you to fill configuration programmatically so you can
	 * loop through the enum instances and add the default properties.
	 *
	 * {@snippet :
	 * public class ContextPathProvider implements KeyValuesServiceProvider.KeyValuesProvider {
	 * 	public enum ContextPath { // for example purposes this an inner class
	 * 		ACCOUNT("/account"), PROFILE("/profile");
	 *
	 * 		final String path;
	 *
	 * 		ContextPath(String path) {
	 * 			this.path = path;
	 * 		}
	 * 	}
	 *
	 * 	public void provide(KeyValues.Builder builder, LoaderContext context) {
	 * 		for (ContextPath p : ContextPath.values()) {
	 * 			builder.add("contextpath." + p.name().toLowerCase(), p.path);
	 * 		}
	 * 	}
	 * }
	 * }
	 *
	 * Note that providers can make {@link KeyValuesResource#KEY_LOAD} keys to load other
	 * resources if they really would like to have resources loaded.
	 *
	 * @see KeyValuesResource#SCHEMA_PROVIDER
	 */
	public non-sealed interface KeyValuesProvider extends KeyValuesServiceProvider {

		/**
		 * A context provided to {@link KeyValuesProvider} implementations to supply
		 * necessary dependencies and services.
		 */
		public sealed interface ProviderContext extends Context {

		}

		/**
		 * Implementations should use the mutable builder to add key values. The provided
		 * builder will already be preconfigured based on the {@link #name()} and other
		 * resource meta data.
		 * <p>
		 * <strong> It is highly recommended that you do not initialize to many things in
		 * this call that would initialize logging or something that needs configuration!
		 * </strong>
		 * <p>
		 * If you need to use logging use the EZKV logging:
		 * {@link KeyValuesEnvironment#getLogger()} which will by default not initialize a
		 * logging framework.
		 * @param context current load context should be used mainly to use
		 * {@link KeyValuesEnvironment#getLogger()} and to help parse literal strings that
		 * are properties.
		 * @param builder used to add key values.
		 */
		void provide(ProviderContext context, KeyValues.Builder builder);

		/**
		 * The name used to identify where the key values are coming from. The name should
		 * follow {@value KeyValuesResource#RESOURCE_NAME_REGEX} regex and because by
		 * default the {@link Class#getSimpleName()} is used this method should be
		 * implemented if your class does not follow that naming regex.
		 * @return by default {@link Class#getSimpleName()}.
		 */
		default String name() {
			return this.getClass().getSimpleName();
		}

	}

	/**
	 * A service provider interface for finding {@link KeyValuesLoader} implementations
	 * that can handle specific {@link KeyValuesResource} instances.
	 */
	public non-sealed interface KeyValuesLoaderFinder extends KeyValuesServiceProvider {

		/**
		 * A context provided to {@link KeyValuesLoaderFinder} implementations to supply
		 * necessary dependencies and services for creating a {@link KeyValuesLoader}.
		 */
		public sealed interface LoaderContext extends Context {

		}

		/**
		 * Finds a {@link KeyValuesLoader} capable of loading the specified
		 * {@link KeyValuesResource} using the provided {@link LoaderContext}. <strong>If
		 * the resource cannot be found the returned {@link KeyValuesLoader} should throw
		 * a {@link FileNotFoundException} or {@link NoSuchFileException} to indicate not
		 * found and not Optional.empty which conversely means no matching loader could be
		 * found based on the resource. </strong>
		 * @param context the context containing dependencies and services
		 * @param resource the resource for which a loader is sought
		 * @return an {@link Optional} containing the loader if found, or empty if not
		 * @see KeyValuesLoader#load()
		 */
		public Optional<? extends KeyValuesLoader> findLoader(LoaderContext context, KeyValuesResource resource);

	}

	/**
	 * A service provider interface (SPI) for filtering key values after a resource is
	 * loaded.
	 *
	 * <p>
	 * Filters are applied to modify, rename, or remove key-value pairs without altering
	 * the original resource. This can be useful for transforming keys to a desired format
	 * or eliminating unnecessary entries before the key values are processed further.
	 *
	 * <h2>Usage</h2>
	 * <p>
	 * A {@code KeyValuesFilter} is invoked after a {@link KeyValuesResource} is loaded
	 * but before the resulting {@link KeyValues} are passed to the next stage of
	 * processing. The filter uses a filter name and optional parameters to determine how
	 * the key values should be modified.
	 * </p>
	 *
	 * Filters are enabled by using the {@link KeyValuesResource#KEY_FILTER} key where the
	 * default syntax is <code>_filter_[resourceName]_[filterId]=expression</code>.
	 *
	 * <h2>Builtin Filters</h2>
	 *
	 * <h3><code>grep</code></h3>
	 *
	 * Grep will filter keys by a regular expression. If the expression matches any part
	 * of the key the key value is included.
	 *
	 * {@snippet lang = properties :
	 *
	 * _load_resource=...
	 * _filter_resource_grep=someregex
	 *
	 * }
	 *
	 * <h3><code>sed</code></h3>
	 *
	 * Sed can change the keys name with subsitution or drop keys altogether. It follows a
	 * subset of the UNIX sed command where "<code>s</code>" and "<code>d</code>" are
	 * supported commands.
	 *
	 * {@snippet lang = properties :
	 *
	 * _load_resource=...
	 * _filter_resource_sed=/match/ s/replace/withme/g
	 *
	 * }
	 *
	 * <h2>Extensibility</h2>
	 * <p>
	 * Implementations of {@code KeyValuesFilter} can be registered using the
	 * {@code provides} directive in {@code module-info.java} or the
	 * {@code META-INF/services} mechanism with the fully qualified name of
	 * {@link KeyValuesServiceProvider}. <strong>Ideally the filter only applies filtering
	 * based on the passed in filter name.</strong> Otherwise it should just return the
	 * passed in key values.
	 * </p>
	 *
	 * <p>
	 * For example, to provide a custom implementation:
	 * </p>
	 * <pre>{@code
	 * module my.module {
	 *     provides io.jstach.ezkv.kvs.KeyValuesServiceProvider with my.module.MyKeyValuesFilter;
	 * }
	 * }</pre>
	 *
	 * <h2>Method Details</h2>
	 *
	 * <p>
	 * The {@link #filter(FilterContext, KeyValues, Filter)} method performs the filtering
	 * operation.
	 * </p>
	 *
	 * <h2>Implementation Notes</h2>
	 *
	 * In general it is not recommend that filters change values of key values however if
	 * a filter would like to modify the value of key value (not true modify since key
	 * values are immutable but copy) {@link KeyValue#withSealedValue(String)} should be
	 * used otherwise the value will be likely replaced by downstream interpolation.
	 */
	public non-sealed interface KeyValuesFilter extends KeyValuesServiceProvider {

		// TODO maybe the filter context should have the currently loaded key values so
		// far

		/**
		 * Provides contextual information to a filter, including the environment and any
		 * parameters specified for the filtering operation.
		 *
		 * @param environment the environment used to access system-level properties and
		 * resources
		 * @param variables current variable store
		 * @param parameters resource parameters that come from
		 * {@link KeyValuesResource#KEY_PARAM} keys.
		 * @param keyValueIgnore keys that should be ignored and not filtered if the
		 * predicate returns <code>true</code>.
		 * @see KeyValuesResource#FLAG_NO_FILTER_RESOURCE_KEYS
		 */
		public record FilterContext(KeyValuesEnvironment environment, //
				Variables variables, //
				Parameters parameters, //
				Predicate<KeyValue> keyValueIgnore) {
			/**
			 * Wil resolve the currently enabled profiles. TODO this may change before 1.0
			 * @return list of profiles
			 */
			public List<String> profiles() {
				return DefaultLoaderContext.profiles(environment, variables, parameters);
			}
		}

		/**
		 * A filter description with filter identifier and expression which is the DSL
		 * code that the filter will parse use to do filtering.
		 *
		 * @param filter filter identifier.
		 * @param expression string code used by the filter to determine filtering.
		 * @param name used to differentiate multiple calls to the same filter type when
		 * looking up parameters. Defaults to empty string.
		 */
		public record Filter(String filter, String expression, String name) {
			/*
			 * TODO remove filter name
			 */
		}

		/**
		 * Applies a filter to the given key-value pairs. The filter should ideally only
		 * be applied if the passed filter parameter matches (filter name) otherwise the
		 * filter should return {@link Optional#empty()} which indicates the filter did
		 * not match or was not applicable. Note that if the filter is applicable but
		 * wants to return an empty key values it should return an Optional of empty key
		 * values and not {@link Optional#empty()}.
		 * @param context the filter context providing the environment and parameters
		 * @param keyValues the key-value pairs loaded by the current resource to be
		 * filtered
		 * @param filter filter description
		 * @return an optional {@link KeyValues} instance with the filtered key-value
		 * pairs
		 * @throws IllegalArgumentException or subclass if the filter expression is
		 * malformed.
		 * @throws KeyValuesException if some other key value parsing like error happens.
		 */
		Optional<KeyValues> filter(FilterContext context, KeyValues keyValues, Filter filter)
				throws IllegalArgumentException, KeyValuesException;

	}

	/**
	 * A service provider interface for finding {@link KeyValuesMedia} implementations
	 * based on various attributes such as file extensions, media types, and URIs.
	 */
	public non-sealed interface KeyValuesMediaFinder extends KeyValuesServiceProvider {

		/**
		 * Finds a {@link KeyValuesMedia} based on the attributes of a given
		 * {@link KeyValuesResource}.
		 * @param resource the resource for which to find the media type
		 * @return an {@link Optional} containing the media if found, or empty if not
		 */
		default Optional<KeyValuesMedia> findByResource(KeyValuesResource resource) {
			String mediaType = resource.mediaType();
			if (mediaType == null) {
				return findByUri(resource.uri());
			}
			return findByMediaType(mediaType);
		}

		/**
		 * Finds a {@link KeyValuesMedia} based on a file extension.
		 * @param ext the file extension (e.g., "properties")
		 * @return an {@link Optional} containing the media if found, or empty if not
		 */
		public Optional<KeyValuesMedia> findByExt(String ext);

		/**
		 * Finds a {@link KeyValuesMedia} based on a media type string.
		 * @param mediaType the media type (e.g., "application/json")
		 * @return an {@link Optional} containing the media if found, or empty if not
		 */
		public Optional<KeyValuesMedia> findByMediaType(String mediaType);

		/**
		 * Finds a {@link KeyValuesMedia} based on a {@link URI}.
		 * @param uri the URI to analyze
		 * @return an {@link Optional} containing the media if found, or empty if not
		 */
		public Optional<KeyValuesMedia> findByUri(URI uri);

	}

}

/**
 * A context provided to {@link KeyValuesLoaderFinder} implementations to supply necessary
 * dependencies and services for creating a {@link KeyValuesLoader}.
 */
sealed interface Context {

	/**
	 * Retrieves the {@link KeyValuesEnvironment} for accessing system-level resources and
	 * properties.
	 * @return the environment instance
	 */
	KeyValuesEnvironment environment();

	/**
	 * Retrieves the {@link KeyValuesMediaFinder} for finding media types.
	 * @return the media finder instance
	 */
	KeyValuesMediaFinder mediaFinder();

	/**
	 * Retrieves the {@link Variables} used for interpolation.
	 * @return the variables instance
	 */
	Variables variables();

	/**
	 * Finds and returns a required parser for the specified {@link KeyValuesResource}.
	 * Throws an exception if no parser is found.
	 * @param resource the resource for which to find a parser
	 * @return the parser for the resource
	 * @throws KeyValuesMediaException if no parser is found for the resource's media type
	 */
	default KeyValuesMedia.Parser requireParser(KeyValuesResource resource) throws KeyValuesMediaException {
		var media = mediaFinder() //
			.findByResource(resource) //
			.orElseThrow(() -> new KeyValuesMediaException("Media Type not found. resource: " + resource));
		return new SafeParser(media.parser(resource.parameters()), media.getMediaType());
	}

	/**
	 * Formats a {@link KeyValuesResource} into its key-value representation and applies
	 * it to a given consumer. This method allows serializing the resource into key-value
	 * pairs.
	 * @param resource the resource to format
	 * @param consumer the consumer to apply the formatted key-value pairs
	 */
	void formatResource(KeyValuesResource resource, BiConsumer<String, String> consumer);

	/**
	 * Formats a resource parameter key how it is represented as key in the parsed
	 * resource.
	 * @param resource resource to base the full parameter name on.
	 * @param parameterName the short parameter from
	 * {@link KeyValuesResource#parameters()}.
	 * @return FQ parameter name.
	 */
	String formatParameterKey(KeyValuesResource resource, String parameterName);

	/**
	 * Finds a list of profiles based on context and passed in parameters.
	 * @param parameters from resource or filter.
	 * @return list of profiles
	 */
	default List<String> profiles(Parameters parameters) {
		return DefaultLoaderContext.profiles(environment(), variables(), parameters);
	}

}

record DefaultLoaderContext(KeyValuesEnvironment environment, KeyValuesMediaFinder mediaFinder, Variables variables,
		KeyValuesResourceParser resourceParser,
		List<? extends KeyValuesProvider> providers) implements LoaderContext, ProviderContext {
	static LoaderContext of(KeyValuesSystem system, Variables variables, KeyValuesResourceParser resourceParser) {
		List<? extends KeyValuesProvider> providers = switch (system) {
			case DefaultKeyValuesSystem d -> d.providers();
		};
		return new DefaultLoaderContext(system.environment(), system.mediaFinder(), variables, resourceParser,
				providers);
	}

	@Override
	public void formatResource(KeyValuesResource resource, BiConsumer<String, String> consumer) {
		resourceParser.formatResource(resource, consumer);
	}

	@Override
	public String formatParameterKey(KeyValuesResource resource, String parameterName) {
		return resourceParser.formatParameterKey(resource, parameterName);
	}

	/**
	 * Finds a list of profiles based on context and passed in parameters.
	 * @param parameters from resource or filter.
	 * @return list of profiles
	 * @throws FileNotFoundException
	 */
	static List<String> profiles(KeyValuesEnvironment environment, Variables variables, Parameters parameters) {
		var vars = Variables.builder().add(parameters).add(variables.renameKey(environment::qualifyMetaKey)).build();
		var profile = vars.findEntry("profile", "profiles").map(e -> e.getValue()).orElse(null);
		if (profile == null) {
			return List.of();
		}
		List<String> profiles = Stream.of(profile.split(",")).filter(p -> !p.isBlank()).distinct().toList();
		return profiles;
	}

}