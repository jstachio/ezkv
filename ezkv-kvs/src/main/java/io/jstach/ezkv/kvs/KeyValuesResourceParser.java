package io.jstach.ezkv.kvs;

import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/*
 * The idea here is to keep all the parsing logic separated from the core domain so that
 * we can switch the key value patterns of loading.
 */

/**
 * This interface handles parsing and determining special keys for loading additional
 * key-value pairs. The logic for parsing is separated from the core domain to allow
 * flexibility in switching key-value patterns for loading resources.
 *
 * <p>
 * The `KeyValuesResourceParser` processes `KeyValues` streams to extract and format
 * resource references, supporting the recursive loading mechanism in ezkv.
 *
 * <p>
 * Example usage might include parsing key-values to find resource URIs or formatting a
 * resource into its key-value representation.
 *
 * @apiNote This is not public for a variety of complex reasons that maybe revisited.
 */
sealed interface KeyValuesResourceParser permits DefaultKeyValuesResourceParser {

	/**
	 * Parses a {@link KeyValues} stream and returns a list of {@link KeyValuesResource}
	 * objects representing resources that should be loaded.
	 * @param keyValues the key-values to parse for resource references
	 * @param loadFlags loadFlags to be inherited.
	 * @return a list of resources parsed from the key-values
	 * @throws KeyValuesResourceParserException if key values has an incorrect resource
	 * key format.
	 */
	List<? extends InternalKeyValuesResource> parseResources(KeyValues keyValues, Set<LoadFlag> loadFlags)
			throws KeyValuesResourceParserException;

	// TODO we should probably make a ParsedKeyValuesResource or
	// NormalizedKeyValuesResource
	// For now Internal is doing double duty.
	/**
	 * Because the resource URI might contain meta data we may need to reparse to build
	 * the correct resource representation.
	 * @param resource resoure that may need to be reparse.
	 * @return internal key values resource that is normalized.
	 * @throws KeyValuesResourceParserException if key values has an incorrect resource
	 * key format.
	 */
	InternalKeyValuesResource normalizeResource(KeyValuesResource resource) throws KeyValuesResourceParserException;

	/**
	 * Filters out the key-values that are related to resource loading from a given
	 * {@link KeyValues} stream. This is to remove all the resource meta key value pairs
	 * that are not needed in the final result as well as avoid collisions in resources
	 * loaded after.
	 * @param keyValues the key-values to filter
	 * @return a {@link KeyValues} instance with resource-related key-values removed
	 */
	KeyValues filterResources(KeyValues keyValues);

	/**
	 * Tests if a key value is a resource key.
	 * @param kv key value to test
	 * @return true if it is a resource key.
	 */
	boolean isResourceKey(KeyValue kv);

	/**
	 * Converts a {@link KeyValuesResource} into its key-value representation and applies
	 * the resulting key-value pairs to a provided consumer. This method effectively
	 * serializes the resource into a format that would be parsed back into a resource by
	 * the library.
	 *
	 * <p>
	 * The output can include keys that represent the URI, parameters, flags, and other
	 * metadata associated with the resource.
	 * @param resource the resource to format
	 * @param consumer the consumer to apply the serialized key-value pairs
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

}

final class KeyValuesResourceParserException extends KeyValuesException {

	private static final long serialVersionUID = 2616767401332338068L;

	public KeyValuesResourceParserException(String message) {
		super(message);
	}

	public KeyValuesResourceParserException(String message, Throwable cause) {
		super(message, cause);
	}

}