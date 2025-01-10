package io.jstach.ezkv.kvs;

import java.io.FileNotFoundException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import io.jstach.ezkv.kvs.KeyValuesServiceProvider.KeyValuesFilter.Filter;
import io.jstach.ezkv.kvs.KeyValuesServiceProvider.KeyValuesFilter.FilterContext;
import io.jstach.ezkv.kvs.Variables.Parameters;

/**
 * Represents a resource that contains key-value pairs to be loaded and processed. A
 * {@code KeyValuesResource} includes a URI, optional parameters, and metadata flags that
 * determine its behavior during loading.
 *
 * <h2>Resource URIs</h2>
 * <p>
 * Out of the box, Ezkv supports the following URI schemas:
 * </p>
 *
 * <ul>
 * <li>{@value io.jstach.ezkv.kvs.KeyValuesResource#SCHEMA_CLASSPATH} - classpath
 * resource</li>
 * <li>{@value io.jstach.ezkv.kvs.KeyValuesResource#SCHEMA_CLASSPATHS} - for merging
 * mulitple resources with the same name on the classpath (use with caution).</li>
 * <li>{@value io.jstach.ezkv.kvs.KeyValuesResource#SCHEMA_FILE} - file resource</li>
 * <li>{@value io.jstach.ezkv.kvs.KeyValuesResource#SCHEMA_SYSTEM} - System
 * properties</li>
 * <li>{@value io.jstach.ezkv.kvs.KeyValuesResource#SCHEMA_ENV} - Environment
 * variables</li>
 * <li>{@value io.jstach.ezkv.kvs.KeyValuesResource#SCHEMA_CMD} - Command line argument
 * pairs separated by <code>=</code></li>
 * <li>{@value io.jstach.ezkv.kvs.KeyValuesResource#SCHEMA_STDIN} - Allows Unix piping of
 * key values, often useful for passwords</li>
 * <li>{@value io.jstach.ezkv.kvs.KeyValuesResource#SCHEMA_PROFILE} - Will load multiple
 * resources based on a CSV of profiles where the profile name replaces part of the
 * URI</li>
 * <li>{@value io.jstach.ezkv.kvs.KeyValuesResource#SCHEMA_PROVIDER} - Will load reference
 * key values provided from other modules/layers and is way for other modules/layers to
 * share default configuration. See {@link KeyValuesServiceProvider.KeyValuesProvider}.
 * </li>
 * </ul>
 *
 * <h2>Resource Keys</h2>
 * <p>
 * Resources can have special keys associated with them. The most important key is the
 * load key ({@link #KEY_LOAD}), which assigns a name to the resource. The resource name
 * is the text after the {@value #DEFAULT_KEY_PREFIX} + {@value #KEY_LOAD} +
 * {@value #DEFAULT_KEY_SEP} which becomes the resource name and the value of the key
 * should be a URI. The resource name must match the regex: {@value #RESOURCE_NAME_REGEX}.
 * For example <code>_load_myresource=URI</code> would have the {@linkplain #name() name}
 * "<code>myresource</code>".
 * </p>
 * <p>
 * All other keys can be specified in two ways:
 * </p>
 * <ol>
 * <li><strong>In-URI keys</strong>: As query parameters in the resource's URI (these keys
 * will be removed before the resource is loaded).</li>
 * <li><strong>In-Resource keys</strong>: As additional key-value pairs within the same
 * resource, using the resource name as part of the key (they keys will be removed from
 * the final result).</li>
 * </ol>
 *
 * <em>The parameters are combined from both the "In-URI keys" and "In-Resource keys" but
 * "In-Resource keys" take prededence on collision.</em> One advantage that "In-URI keys"
 * have is that they do not need the resource name as that can be deduced and order is
 * guaranteed which is important for filters.
 *
 * <table class="table">
 * <caption><strong>Resource Keys using default syntax</strong></caption> <thead>
 * <tr>
 * <th>Resource Key(s)</th>
 * <th>Resource Syntax</th>
 * <th>URI Syntax</th>
 * <th>Description</th>
 * </tr>
 * </thead> <tbody>
 * <tr>
 * <td>{@value io.jstach.ezkv.kvs.KeyValuesResource#KEY_LOAD}</td>
 * <td><code>_load_[resource]</code></td>
 * <td><strong>N/A</strong></td>
 * <td>Specifies a resource to load. The resource name is defined as the content following
 * <code>_load_</code>. It should be alphanumeric with no spaces.</td>
 * </tr>
 * <tr>
 * <td>{@value io.jstach.ezkv.kvs.KeyValuesResource#KEY_FLAGS} or
 * {@value io.jstach.ezkv.kvs.KeyValuesResource#KEY_FLAG}</td>
 * <td><code>_flags_[resource]</code></td>
 * <td><code>_flags</code></td>
 * <td>Defines flags associated with a resource. Replace <code>[resource]</code> with the
 * actual resource name.</td>
 * </tr>
 * <tr>
 * <td>{@value io.jstach.ezkv.kvs.KeyValuesResource#KEY_MEDIA_TYPE} or
 * {@value io.jstach.ezkv.kvs.KeyValuesResource#KEY_MIME}</td>
 * <td><code>_mediaType_[resource]</code></td>
 * <td><code>_mediaType</code></td>
 * <td>Specifies the media type of a resource. Replace <code>[resource]</code> with the
 * actual resource name.</td>
 * </tr>
 * <tr>
 * <td>{@value io.jstach.ezkv.kvs.KeyValuesResource#KEY_PARAM} or
 * {@value io.jstach.ezkv.kvs.KeyValuesResource#KEY_PARM}</td>
 * <td><code>_param_[resource]_[name]</code></td>
 * <td><code>_param_[name]</code></td>
 * <td>Defines parameters associated with a resource. Replace <code>[resource]</code> with
 * the actual resource name, and <code>[name]</code> with the parameter name, which should
 * be alphanumeric with no spaces.</td>
 * </tr>
 * <tr>
 * <td>{@value io.jstach.ezkv.kvs.KeyValuesResource#KEY_FILTER} or
 * {@value io.jstach.ezkv.kvs.KeyValuesResource#KEY_FILT}</td>
 * <td><code>_filter_[resource]_[filter]</code></td>
 * <td><code>_filter_[filter]</code></td>
 * <td>Specifies filters to apply to a resource. Replace <code>[resource]</code> with the
 * actual resource name, and <code>[filter]</code> with the filter identifier, both of
 * which should be alphanumeric with no spaces.</td>
 * </tr>
 * </tbody>
 * </table>
 * <h3>Resource Flags</h3> As mentioned above flags are set with the {@value #KEY_FLAGS}
 * key.
 *
 * <table class="table">
 * <caption><strong>Resource loading flags</strong></caption> <thead>
 * <tr>
 * <th>Flag Value</th>
 * <th>Description</th>
 * </tr>
 * </thead> <tbody>
 * <tr>
 * <td>{@value io.jstach.ezkv.kvs.KeyValuesResource#FLAG_NO_REQUIRE}</td>
 * <td>Indicates that the resource is optional and no error should occur if it is not
 * found. Synonym: {@value io.jstach.ezkv.kvs.KeyValuesResource#FLAG_OPTIONAL}</td>
 * </tr>
 * <tr>
 * <td>{@value io.jstach.ezkv.kvs.KeyValuesResource#FLAG_SENSITIVE}</td>
 * <td>Marks the resource as containing sensitive key-value pairs, which should not be
 * displayed or included in output such as <code>toString</code>.</td>
 * </tr>
 * <tr>
 * <td>{@value io.jstach.ezkv.kvs.KeyValuesResource#FLAG_NO_ADD}</td>
 * <td>Indicates that the key-value pairs from the resource should only be added to
 * variables and not to the final resolved key-value set.</td>
 * </tr>
 * <tr>
 * <td>{@value io.jstach.ezkv.kvs.KeyValuesResource#FLAG_NO_INTERPOLATE}</td>
 * <td>Disables interpolation entirely for key-value pairs loaded from this resource.</td>
 * </tr>
 * </tbody>
 * </table>
 *
 * <p>
 * <em>Note: flags can be negated textual by appending <code>NO_</code> /
 * <code>NOT_</code> or removing <code>NO_</code> (if present) and are case
 * insensitive</em>
 * </p>
 *
 * <h3>Resource Filters</h3> As mentioned previously filters are set with the
 * {@value #KEY_FILTER} key.
 * <p>
 * Out of the box, Ezkv supports the following resource filters:
 * </p>
 * <table class="table">
 * <caption><strong>Resource Filters</strong></caption> <thead>
 * <tr>
 * <th>Filter</th>
 * <th>Description</th>
 * </tr>
 * </thead> <tbody>
 * <tr>
 * <td>{@value io.jstach.ezkv.kvs.KeyValuesResource#FILTER_GREP}</td>
 * <td>Grep filter, similar to Unix grep, allows picking keys based on matching
 * regex.</td>
 * </tr>
 * <tr>
 * <td>{@value io.jstach.ezkv.kvs.KeyValuesResource#FILTER_SED}</td>
 * <td>Sed filter, similar to Unix sed, can rename or delete key-value keys using
 * substitution (<code>s</code>) or delete (<code>d</code>) commands.</td>
 * </tr>
 * <tr>
 * <td>{@value io.jstach.ezkv.kvs.KeyValuesResource#FILTER_JOIN}</td>
 * <td>Join filter can join duplicate keys' values with the separator being the expression
 * akin to <code>String.join(expression)</code>.</td>
 * </tr>
 * </tbody>
 * </table>
 *
 * <h4>Filter Target</h4> The {@value io.jstach.ezkv.kvs.KeyValuesResource#FILTER_GREP}
 * and {@value io.jstach.ezkv.kvs.KeyValuesResource#FILTER_SED} filters by default target
 * the keys for search and manipulation. These filter can target the value by suffixing
 * with {@value #FILTER_TARGET_VALUE} or {@link #FILTER_TARGET_VAL}.
 *
 * <h3>Example</h3>
 *
 * Example usage for building a {@code KeyValuesResource}:
 * {@snippet :
 * var resource = KeyValuesResource.builder("classpath:/config.properties")
 * 	.name("config")
 * 	.flags("optional,interpolate")
 * 	.parameter("key1", "value1")
 * 	.sensitive(true)
 * 	.build();
 * }
 *
 * @see KeyValuesServiceProvider
 * @see KeyValuesLoader.Builder#add(KeyValuesResource)
 * @see #builder(URI)
 */
public sealed interface KeyValuesResource extends NamedKeyValuesSource, KeyValueReference
		permits InternalKeyValuesResource {

	/**
	 * Resource names must match {@value #RESOURCE_NAME_REGEX} regular expression which is
	 * effectively only alphanumeric and no spaces.
	 */
	public static final String RESOURCE_NAME_REGEX = "[a-zA-Z0-9]+";

	/**
	 * Indicates that the resource is optional and no error should occur if it is not
	 * found. {@linkplain KeyValuesLoader Loaders} can indicate not found by throwing
	 * either {@link FileNotFoundException} or {@link NoSuchFileException}. If the
	 * resource is missing during a full load it will be logged with
	 * {@link KeyValuesEnvironment.Logger#missing(KeyValuesResource, Exception)}. Note
	 * that a resource may just not have any key values but is still found. By default
	 * resources are normally required to exist.
	 * @see #FLAG_NO_EMPTY
	 */
	public static final String FLAG_NO_REQUIRE = "NO_REQUIRE";

	/**
	 * A synonym for {@link #FLAG_NO_REQUIRE}.
	 */
	public static final String FLAG_OPTIONAL = "OPTIONAL";

	/**
	 * A synonym for {@link #FLAG_NO_REQUIRE}.
	 */
	public static final String FLAG_NOT_REQUIRED = "NOT_REQUIRED";

	/**
	 * Indicates that the resource should not be empty. Typically used to enforce that the
	 * resource contains at least one key-value pair. This is different than
	 * {@link #FLAG_NO_REQUIRE} which is if the resource does not exist at all. By default
	 * empty resources are normally allowed.
	 */
	public static final String FLAG_NO_EMPTY = "NO_EMPTY";

	/**
	 * Specifies that the resource is locked and its properties should not be overridden.
	 * This is distinct from {@link #FLAG_NO_REPLACE}, which has different semantics.
	 */
	public static final String FLAG_LOCK = "LOCK";

	/**
	 * Ensures that the resource can only add new key-value pairs and cannot replace
	 * existing ones.
	 */
	public static final String FLAG_NO_REPLACE = "NO_REPLACE";

	/**
	 * Indicates that the key-value pairs from the resource should only be added to
	 * variables and not to the final resolved key-value set.
	 */
	public static final String FLAG_NO_ADD = "NO_ADD";

	/**
	 * Prevents the resource from invoking <code>_load</code> calls to load additional
	 * child resources.
	 */
	public static final String FLAG_NO_LOAD_CHILDREN = "NO_LOAD_CHILDREN";

	/**
	 * Disables interpolation entirely for key-value pairs loaded from this resource.
	 */
	public static final String FLAG_NO_INTERPOLATE = "NO_INTERPOLATE";

	/**
	 * Marks the resource as containing sensitive key-value pairs, which should not be
	 * displayed or included in output such as `toString`.
	 */
	public static final String FLAG_SENSITIVE = "SENSITIVE";

	/**
	 * Hints to filters that they should retain and not alter resource keys.
	 * @see FilterContext#keyValueIgnore()
	 */
	public static final String FLAG_NO_FILTER_RESOURCE_KEYS = "NO_FILTER_RESOURCE_KEYS";

	/**
	 * Specifies that the resource should not be reloaded once it has been loaded.
	 */
	public static final String FLAG_NO_RELOAD = "NO_RELOAD";

	/**
	 * Indicates that the resource should pass its flags down to child resources
	 * regardless if the child resource has them set or not. To disable propagation on a
	 * child one could attach <code>NO_</code> in front of this flag.
	 */
	public static final String FLAG_PROPAGATE = "PROPAGATE";

	/**
	 * By default resource keys are prefixed with {@value #DEFAULT_KEY_PREFIX}. Note that
	 * other keys that are not resource keys may have this prefix and will not be filtered
	 * out.
	 */
	public static final String DEFAULT_KEY_PREFIX = "_";

	/**
	 * By default resource keys use this as separator to separate the resource key and
	 * resource name ({@link #KEY_LOAD}) as well as other key properties (filter id for
	 * {@link #KEY_FILTER} and parameter name for {@link #KEY_PARAM}).
	 */
	public static final String DEFAULT_KEY_SEP = "_";

	/**
	 * Key to specify a resource to load. The resource name is defined as the content
	 * following * <code>_load_</code>. It should be alphanumeric with no spaces.
	 * @see #RESOURCE_NAME_REGEX
	 */
	public static final String KEY_LOAD = "load";

	/**
	 * Defines flags associated with a resource.
	 * @see #KEY_FLAG
	 */
	public static final String KEY_FLAGS = "flags";

	/**
	 * Key for specifying the media type (or file extension) of a resource.
	 * {@link KeyValuesSystem#mediaFinder()} is then typically used for the lookup.
	 * @see #KEY_MIME
	 * @see KeyValuesMedia
	 */
	public static final String KEY_MEDIA_TYPE = "mediaType";

	/**
	 * Represents the key for specifying parameters associated with a resource and are
	 * usually suffixed with a separator and name of the parameter.
	 */
	public static final String KEY_PARAM = "param";

	/**
	 * Key for specifying filters to apply to the resource. The name after the last
	 * {@link #DEFAULT_KEY_SEP} is the filter id. The value of the key is the filter
	 * expression and its syntax and semantics vary by filter.
	 * @see KeyValuesServiceProvider.KeyValuesFilter
	 * @see #FILTER_SED
	 * @see #FILTER_GREP
	 */
	public static final String KEY_FILTER = "filter";

	/**
	 * A synonym for {@link #KEY_FLAGS}.
	 */
	public static final String KEY_FLAG = "flag";

	/**
	 * A synonym for {@link #KEY_MEDIA_TYPE}.
	 */
	public static final String KEY_MIME = "mime";

	/**
	 * A synonym for {@link #KEY_PARAM}.
	 */
	public static final String KEY_PARM = "parm";

	/**
	 * A synonym for {@link #KEY_PARAM}.
	 */
	public static final String KEY_P = "p";

	/**
	 * A synonym for {@link #KEY_FILTER}.
	 */
	public static final String KEY_FILT = "filt";

	// @formatter:off
	/**
	 * Classpath resource is loaded using
	 * {@link KeyValuesEnvironment#getResourceLoader()}.
	 *
	 * {@snippet lang=properties :
	 * _load_app=classpath:///app.properties
	 * }
	 */
	// @formatter:on
	public static final String SCHEMA_CLASSPATH = "classpath";

	// @formatter:off
	/**
	 * Classpaths (<strong> note the plural "s"</strong>) resource is loaded using
	 * {@link KeyValuesEnvironment#getResourceLoader()}. This is analogous
	 * to Spring's <code>classpath*</code> resource support where all
	 * matching resources of the same name are loaded.
	 *
	 * {@snippet lang=properties :
	 * _load_app=classpaths:///app.properties
	 * }
	 * For security reasons this resource will not allow
	 * the found resources to load children.
	 */
	// @formatter:on
	public static final String SCHEMA_CLASSPATHS = "classpaths";

	// @formatter:off
	/**
	 * Will load provider(s) reference key values.
	 *
	 * {@snippet lang=properties :
	 * # load all providers registered
	 * _load_providers=provider:///
	 * # or load a specific provider from another module
	 * _load_provider_db=provider:///RepositoryLayerKeyValuesProvider
	 * }
	 *
	 * It is recommend that you do enable the service loader
	 * with {@link KeyValuesSystem.Builder#useServiceLoader()}
	 * and that you use this resource schema very early so
	 * that downstream key values can override it.
	 *
	 * @see KeyValuesServiceProvider.KeyValuesProvider
	 */
	// @formatter:on
	public static final String SCHEMA_PROVIDER = "provider";

	// @formatter:off
	/**
	 * File resource which is the default if no schema is given.
	 * {@snippet lang=properties :
	 * _load_somefile=file:///./app.properties
	 * _load_another=another.properties
	 * }
	 */
	// @formatter:on
	public static final String SCHEMA_FILE = "file";

	/**
	 * System properties which come from
	 * {@link KeyValuesEnvironment#getSystemProperties()}.
	 * <p>
	 * This schema can take a path that fetches a key value, where the value is used as
	 * the source of the key values. If no path is provided, all key values of the
	 * resource will be loaded.
	 *
	 */
	public static final String SCHEMA_SYSTEM = "system";

	/**
	 * Environment variables which come from {@link KeyValuesEnvironment#getSystemEnv()}.
	 * <p>
	 * This schema can take a path that fetches a key value, where the value is used as
	 * the source of the key values. If no path is provided, all key values of the
	 * resource will be loaded.
	 */
	public static final String SCHEMA_ENV = "env";

	/**
	 * Command line argument pairs separated by <code>=</code>.
	 * <p>
	 * This schema can take a path that fetches a key value, where the value is used as
	 * the source of the key values. If no path is provided, all key values of the
	 * resource will be loaded.
	 */
	public static final String SCHEMA_CMD = "cmd";

	// @formatter:off
	/**
	 * Allows Unix piping of key values, often useful for passwords.
	 * <p>
	 * <code>stdin:///</code> without a path assumes that the entire contents of stdin are
	 * in java.util.Properties key-value format. If this is not the case, it is advisable
	 * to specify _mediaType= explicitly with stdin.
	 * </p>
	 * <code>stdin:///</code> can also bind the input content, parsed as a UTF-8 string,
	 * to the key provided in the URI path.
	 *
	 * {@snippet lang=properties :
	 * _load_stdin=stdin:///db.password?_flag=sensitive,optional
	 * }
	 *
	 * Stdin is retrieved from {@link KeyValuesEnvironment#getStandardInput()}.
	 *
	 */
	// @formatter:on
	public static final String SCHEMA_STDIN = "stdin";

	/**
	 * Will load multiple resources based on a CSV of profiles where the profile name
	 * replaces part of the URI.
	 */
	public static final String SCHEMA_PROFILE = "profile.";

	/**
	 * Grep filter just like unix grep allows pickings keys based on matching regex.
	 * @see #FILTER_GREP
	 */
	public static final String FILTER_GREP = "grep";

	/**
	 * Sed filter can rename or delete key values keys just like unix sed by substitution
	 * (<code>s</code>) command or delete command (<code>d</code>).
	 * @see #FILTER_GREP
	 */
	public static final String FILTER_SED = "sed";

	/**
	 * Join filter can join duplicate keys' values with the separator being the expression
	 * akin to <code>String.join(expression)</code>
	 */
	public static final String FILTER_JOIN = "join";

	/**
	 * This suffix on the end of the filter name will tell filters that support to target
	 * the key. Example: suff?_filter_grep_key=grep_key
	 */
	public static final String FILTER_TARGET_KEY = "_key";

	/**
	 * This suffix on the end of the filter name will tell filters that support to target
	 * the value. Example: suff?_filter_grep_value=grep_key
	 * @see #FILTER_TARGET_VAL
	 */
	public static final String FILTER_TARGET_VALUE = "_value";

	/**
	 * Synonym to {@link #FILTER_TARGET_VALUE}.
	 * @see #FILTER_TARGET_VALUE
	 */
	public static final String FILTER_TARGET_VAL = "_val";

	/**
	 * Returns the URI of the resource.
	 * @return the URI of the resource
	 */
	@Override
	public URI uri();

	/**
	 * Returns the parameters associated with the resource which are specified with the
	 * resource key {@value #KEY_PARAM}. Parameters are away to configure custom plugins.
	 * @return the parameters as {@link Parameters}
	 * @see KeyValuesServiceProvider
	 */
	public Parameters parameters();

	/**
	 * Returns the name of the resource.
	 * @return the name of the resource
	 * @see #RESOURCE_NAME_REGEX
	 */
	@Override
	public String name();

	/**
	 * Returns the {@link KeyValue} that references this resource, if any. This is
	 * normally the {@linkplain #KEY_LOAD load key} in the parent resource loaded key
	 * values.
	 * @return the reference {@code KeyValue}, or {@code null} if not applicable
	 */
	@Override
	public @Nullable KeyValue reference();

	/**
	 * Returns the media type of the resource, if specified.
	 * @return the media type, or {@code null} if not specified
	 * @see KeyValuesMedia
	 * @see #KEY_MEDIA_TYPE
	 */
	public @Nullable String mediaType();

	/**
	 * Creates a builder based on the current state of this {@code KeyValuesResource}.
	 * @return a {@code Builder} initialized with the current state
	 */
	public Builder toBuilder();

	// TODO maybe we do not provide this.
	/**
	 * Pretty toString of resource.
	 * @return pretty print resource.
	 */
	default String description() {
		return "uri='" + uri() + "' name='" + name() + "'" + ", reference="
				+ KeyValueReference.toStringRef(this.reference());
	}

	/**
	 * Creates a builder for a {@code KeyValuesResource} from the given URI.
	 * @param uri the URI for the resource
	 * @return a new {@code Builder} instance
	 */
	public static Builder builder(URI uri) {
		return new Builder(uri, uriToName(uri));
	}

	/**
	 * Creates a builder for a {@code KeyValuesResource} from the given URI string.
	 * @param uri the URI string for the resource
	 * @return a new {@code Builder} instance
	 */
	public static Builder builder(String uri) {
		return builder(URI.create(uri));
	}

	private static String uriToName(URI uri) {
		return Base64.getUrlEncoder()
			.withoutPadding()
			.encodeToString(uri.toASCIIString().getBytes(StandardCharsets.US_ASCII));
	}

	/**
	 * Builder class for creating instances of {@code KeyValuesResource}.
	 */
	public final class Builder {

		URI uri;

		String name;

		final Map<String, String> parameters;

		final EnumSet<LoadFlag> flags;

		final List<Filter> filters;

		@Nullable
		KeyValue reference;

		@Nullable
		String mediaType;

		Builder(URI uri, String name) {
			this.uri = uri;
			this.name = DefaultKeyValuesResource.validateResourceName(name);
			this.parameters = new LinkedHashMap<>();
			this.flags = EnumSet.noneOf(LoadFlag.class);
			this.filters = new ArrayList<>();
		}

		Builder(URI uri, String name, Map<String, String> parameters, EnumSet<LoadFlag> flags,
				@Nullable KeyValue reference, @Nullable String mediaType, List<Filter> filters) {
			super();
			this.uri = uri;
			this.name = DefaultKeyValuesResource.validateResourceName(name);
			this.parameters = parameters;
			this.flags = flags;
			this.reference = reference;
			this.mediaType = mediaType;
			this.filters = filters;
		}

		/**
		 * Sets the URI for the resource. The URI may contain resource keys and for a full
		 * listing of that syntax see {@link KeyValuesResource}.
		 * @param uri the URI to set
		 * @return this builder instance
		 * @see KeyValuesResource
		 */
		public Builder uri(URI uri) {
			this.uri = uri;
			return this;
		}

		/**
		 * Sets the name for the resource. The name should match the regex
		 * {@link KeyValuesResource#RESOURCE_NAME_REGEX}. The validation however is done
		 * at build time and not on this call.
		 * @param name the name to set
		 * @return this builder instance
		 */
		public Builder name(String name) {
			this.name = Objects.requireNonNull(name);
			return this;
		}

		/**
		 * Clears all parameters associated with the resource.
		 * @return this builder instance
		 */
		public Builder clearParameters() {
			parameters.clear();
			return this;
		}

		/**
		 * Adds a parameter key-value pair to the resource.
		 * @param key the parameter key
		 * @param value the parameter value
		 * @return this builder instance
		 */
		public Builder parameter(String key, String value) {
			parameters.put(key, value);
			return this;
		}

		/**
		 * Sets the media type for the resource.
		 * @param mediaType the media type to set and can be the actual media type or the
		 * file extension.
		 * @return this builder instance
		 * @see KeyValuesMedia
		 */
		public Builder mediaType(String mediaType) {
			this.mediaType = mediaType;
			return this;
		}

		/**
		 * Sets the media type for the resource.
		 * @param media the media type to set from {@link KeyValuesMedia#getMediaType()}.
		 * @return this builder instance
		 * @see KeyValuesMedia
		 */
		public Builder mediaType(KeyValuesMedia media) {
			return this.mediaType(media.getMediaType());
		}

		/**
		 * Sets or clears the {@value KeyValuesResource#FLAG_NO_INTERPOLATE} flag.
		 * @param flag whether to set or clear the flag
		 * @return this builder instance
		 * @see KeyValuesResource#FLAG_NO_INTERPOLATE
		 */
		public Builder noInterpolation(boolean flag) {
			LoadFlag.NO_INTERPOLATE.set(flags, flag);
			return this;
		}

		/**
		 * Sets or clears the {@value KeyValuesResource#FLAG_NO_ADD} flag.
		 * @param flag whether to set or clear the flag
		 * @return this builder instance
		 */
		public Builder noAdd(boolean flag) {
			LoadFlag.NO_ADD.set(flags, flag);
			return this;
		}

		/**
		 * Marks the resource as sensitive, meaning its values should not be printed.
		 * @param flag whether to mark the resource as sensitive
		 * @return this builder instance
		 * @see KeyValuesResource#FLAG_SENSITIVE
		 */
		public Builder sensitive(boolean flag) {
			LoadFlag.SENSITIVE.set(flags, flag);
			return this;
		}

		/**
		 * Marks the resource as not required.
		 * @param flag true make the resource not required.
		 * @return this
		 */
		public Builder noRequire(boolean flag) {
			LoadFlag.NO_REQUIRE.set(flags, flag);
			return this;
		}

		/**
		 * Marks the resource as not required.
		 * @param flag true make the resource not required.
		 * @return this
		 * @see KeyValuesResource#FLAG_OPTIONAL
		 */
		public Builder optional(boolean flag) {
			LoadFlag.NO_REQUIRE.set(flags, flag);
			return this;
		}

		Builder _flags(Set<LoadFlag> flags) {
			this.flags.clear();
			this.flags.addAll(flags);
			return this;
		}

		Builder _addFlag(LoadFlag flag) {
			flags.add(flag);
			return this;
		}

		Builder _addFilter(Filter filter) {
			filters.add(filter);
			return this;
		}

		/**
		 * Parses a comma-separated string of resource flags and adds or removes the flags
		 * from this builder.
		 *
		 * <p>
		 * See the constants on this class for the available flag names. Flags are
		 * case-insensitive and can be negated or reversed by prefixing them with
		 * <code>"NO_"</code> or <code>"NOT_"</code>, or by removing these prefixes if
		 * they are already present. If a flag is specified multiple times in the input
		 * string, the last occurrence takes precedence.
		 * </p>
		 * <p>
		 * The following demonstrates how to add and reverse resource flags using this
		 * method:
		 * </p>
		 * {@snippet :
		 * var builder = KeyValuesResource.builder(URI.create("classpath:/config.properties"));
		 * builder.addFlags("no_require,no_interpolate,sensitive");
		 *
		 * // The flags "NO_REQUIRE", "NO_INTERPOLATE", and "SENSITIVE" are added to the builder.
		 * }
		 * @param csv a comma-separated, case-insensitive string of flag names
		 * @return this builder instance for chaining
		 */
		public Builder addFlags(String csv) {
			LoadFlag.parseCSV(this.flags, csv);
			return this;
		}

		/**
		 * Builds and returns a {@code KeyValuesResource} based on the current builder
		 * state.
		 * @return a new {@code KeyValuesResource} instance
		 */
		public KeyValuesResource build() {
			return DefaultKeyValuesResource.of(this, false);
		}

		InternalKeyValuesResource buildNormalized() {
			return DefaultKeyValuesResource.of(this, true);
		}

		// For unit test
		InternalKeyValuesResource build(KeyValuesResourceParser parser) throws KeyValuesResourceParserException {
			return parser.normalizeResource(this.build());
		}

		@Override
		public String toString() {
			return "KeyValuesResource.Builder[uri=" + uri + ", name=" + name + ", parameters=" + parameters + ", flags="
					+ flags + ", reference=" + reference + ", mediaType=" + mediaType + "]";
		}

	}

}

sealed interface InternalKeyValuesResource extends KeyValuesResource {

	Set<LoadFlag> loadFlags();

	boolean isRedacted();

	public List<Filter> filters();

	boolean normalized();

}

record DefaultKeyValuesResource(URI uri, //
		String name, //
		Set<LoadFlag> loadFlags, //
		@Nullable KeyValue reference, //
		@Nullable String mediaType, //
		Parameters parameters, List<Filter> filters, boolean normalized) implements InternalKeyValuesResource {

	DefaultKeyValuesResource {
		validateResourceName(name);
		loadFlags = FlagSet.copyOf(loadFlags, LoadFlag.class);
		filters = List.copyOf(filters);
	}

	public static String validateResourceName(String identifier) {
		return KeyValuesSource.validateName(identifier);
	}

	@Override
	public Builder toBuilder() {
		var resource = this;
		var uri = resource.uri();
		var name = resource.name();
		var parameters = new LinkedHashMap<String, String>();
		resource.parameters().forKeyValues(parameters::put);
		var flags = FlagSet.enumSetOf(LoadFlag.class, resource.loadFlags());
		var ref = resource.reference();
		var mediaType = resource.mediaType();
		var filters = resource.filters();
		var b = new KeyValuesResource.Builder(uri, name, parameters, flags, ref, mediaType, filters);
		return b;
	}

	static InternalKeyValuesResource of(KeyValuesResource.Builder builder, boolean normalized) {
		Map<String, String> parameters = new LinkedHashMap<>(builder.parameters);
		URI uri = builder.uri;
		String name = builder.name;
		var reference = builder.reference;
		var loadFlags = builder.flags;
		var mediaType = builder.mediaType;
		Parameters variables = Parameters.of(parameters);
		var filters = builder.filters;
		return new DefaultKeyValuesResource(uri, name, loadFlags, reference, mediaType, variables, filters, normalized);
	}

	@Override
	public boolean isRedacted() {
		// TODO hmm if the resource has sensitive info should its URI be redacated as
		// well?
		// if (LoadFlag.SENSITIVE.isSet(loadFlags())) {
		// return true;
		// }
		var ref = this.reference();
		if (ref == null) {
			return false;
		}
		return ref.isSensitive();
	}

}
