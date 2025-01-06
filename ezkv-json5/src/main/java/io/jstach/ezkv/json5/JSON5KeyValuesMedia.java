package io.jstach.ezkv.json5;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;

import org.jspecify.annotations.Nullable;

import io.jstach.ezkv.json5.internal.JSONParser;
import io.jstach.ezkv.json5.internal.JSONParserOptions;
import io.jstach.ezkv.json5.internal.JSONParserOptions.DuplicateBehavior;
import io.jstach.ezkv.json5.internal.JSONStringify;
import io.jstach.ezkv.json5.internal.JSONValue;
import io.jstach.ezkv.json5.internal.JSONValue.JSONArray;
import io.jstach.ezkv.json5.internal.JSONValue.JSONBoolean;
import io.jstach.ezkv.json5.internal.JSONValue.JSONNull;
import io.jstach.ezkv.json5.internal.JSONValue.JSONNumber;
import io.jstach.ezkv.json5.internal.JSONValue.JSONObject;
import io.jstach.ezkv.json5.internal.JSONValue.JSONString;
import io.jstach.ezkv.kvs.KeyValues;
import io.jstach.ezkv.kvs.KeyValuesMedia;
import io.jstach.ezkv.kvs.KeyValuesResource;
import io.jstach.ezkv.kvs.Variables;

// @formatter:off
/**
 * Parses <a href="https://json5.org/">JSON5</a> to KeyValues. The {@link #parser()} can
 * also be used for strict JSON as JSON5 is a true superset.
 * <p>
 * The constants in this class are important and describe parameters that can be set on the
 * {@link KeyValuesResource}.
 * </p>
 * <p>
 * JSON is not flat and is more of a tree where as EZKV KeyValues are and only
 * support string values. <strong>Thus the parser flattens the JSON
 * based on some heuristics.</strong> JSON objects are easy to flatten but
 * JSON arrays need special handling.
 * </p>
 *
 * Here is an example of the default flattening:
 *
 * {@snippet lang = json :
 * 	{
 * 		"a": {
 * 			"b": [1, 2.0, {"c": 3}],
 * 			"d": "value"
 * 		}
 * 	}
 * }
 *
 * Here is the corresponding key values (notice duplicates):
 *
 * {@snippet lang = properties :
 * a.b=1
 * a.b=2.0
 * a.b.c=3
 * a.d=value
 * }
 *
 * <p>
 * Because EZKV supports duplicates and order matters the data is not lost but this
 * might be confusing. Another option is to generate array indices in the keys.
 * </p>
 *
 * Assume we load a resource with the previous JSON like:
 *
 * {@snippet lang = properties :
 *
 * _load_a=classpath:/a.json5?_param_json5_arraykey=array
 *
 * }
 *
 * We will get flattened key values that look like:
 *
 * {@snippet lang = properties :
 * a.b[0]=1
 * a.b[1]=2.0
 * a.b[2].c=3
 * a.d=value
 * }
 *
 * Because both formats may not be what you want you might want to use a
 * {@linkplain KeyValuesFilter filter}  to clean up the results.
 *
 * <p>
 * This module does have a service loader registration. If you do not wish to use the
 * Service Loader you can add an instance of this class to
 * {@link io.jstach.ezkv.kvs.KeyValuesSystem.Builder}.
 * </p>
 *
 * @apiNote This is not included with the core ezkv module because JSON5 is still evolving
 * and likely to keep changing.
 * @see #ARRAY_KEY_PARAM
 * @see #SEPARATOR_PARAM
 *
 */
// @formatter:on

public final class JSON5KeyValuesMedia implements KeyValuesMedia {

	/**
	 * The media type is {@value #MEDIA_TYPE} and not <code>application/json</code>
	 * however this media will work for normal json.
	 */
	public static final String MEDIA_TYPE = "application/json5";

	/**
	 * The file ext is {@value #FILE_EXT} and not <code>json</code> however this media
	 * will work for <code>json</code> extension.
	 */
	public static final String FILE_EXT = "json5";
	static final String JSON_FILE_EXT = "json";
	static final String JSON_MEDIA_TYPE = "application/json";

	// @formatter:off
	/**
	 * This parameter controls the array key naming scheme
	 * and is set with {@link KeyValuesResource#parameters()}.
	 * The two values are
	 *
	 * {@value #ARRAY_KEY_DUPLICATE_VALUE} (default) and
	 * {@value #ARRAY_KEY_ARRAY_VALUE}.
	 *
	 * An example is:
	 * {@snippet lang = properties :
	 *
	 * _load_a=classpath:/a.json5?_param_json5_arraykey=array
	 *
	 * }
	 */
	// @formatter:on
	public static final String ARRAY_KEY_PARAM = "json5_arraykey";

	private static final String ARRAY_KEY_ALIAS = "json_arraykey";

	/**
	 * Will output array keys using array notation like <code>a[0]</code> instead of the
	 * default which is duplicating the keys.
	 */
	public static final String ARRAY_KEY_ARRAY_VALUE = "array";

	// @formatter:off
	/**
	 * Will duplicate keys for array entries.
	 * For example <code>{ "a" : [ 1, 2 ] }</code>
	 * will be
	 * {@snippet lang = properties :
	 * a=1
	 * a=2
	 * }
	 */
	// @formatter:on
	public static final String ARRAY_KEY_DUPLICATE_VALUE = "duplicate";

	/**
	 * Configures the separator by {@link KeyValuesResource#parameters()} with the key
	 * {@value #SEPARATOR_PARAM} and is by default {@value #DEFAULT_SEPARATOR}.
	 */
	public static final String SEPARATOR_PARAM = "json5_separator";

	private static final String SEPARATOR_PARAM_ALIAS = "json_separator";

	/**
	 * The default JSON path separator.
	 */
	public static final String DEFAULT_SEPARATOR = ".";

	// @formatter:off
	/**
	 * Configures whether or not to use the raw JSON5 number found
	 * instead of the Java converted number in {@link KeyValuesResource#parameters()} with the key
	 * {@value #NUMBER_RAW_PARAM} and is by default <code>false</code>.
	 * An example is if one used hexadecimal <code>-0xC0FFEE</code> it normally
	 * would be converted to an integer.
	 * {@snippet lang = json :
	 * { "number" : -0xC0FFEE}
	 * }
	 * Will yield key values:
	 * {@snippet lang = properties :
	 * # Normally the below would happen
	 * # number=-12648430
	 * # But will now be
	 * number=-0xC0FFEE
	 * }
	 * <strong>Note that exponents in JSON5 work different than in Java so
	 * downstream configuration parsing will have to deal with this
	 * if this is turned on but otherwise most of JSON5 number
	 * format is a compatible subset of Java.
	 * </strong>
	 */
	// @formatter:on
	public static final String NUMBER_RAW_PARAM = "json5_number_raw";

	private static final String NUMBER_RAW_ALIAS = "json_number_raw";

	/**
	 * For service loader.
	 */
	public JSON5KeyValuesMedia() {
	}

	@Override
	public String getMediaType() {
		return MEDIA_TYPE;
	}

	@Override
	public @Nullable String getFileExt() {
		return FILE_EXT;
	}

	@Override
	public Parser parser() {
		return JSON5KeyValuesParser.parser;
	}

	@Override
	public Parser parser(Variables parameters) {
		var arrayKeyOption = Param.ARRAY.requireElse(parameters, ArrayKeyOption::parse, ArrayKeyOption.defaults());
		String separator = Param.SEPARATOR.requireElse(parameters, DEFAULT_SEPARATOR);
		boolean rawNumber = Param.NUMBER.requireElse(parameters, Boolean::parseBoolean, false);
		return new JSON5KeyValuesParser(JSON5KeyValuesParser.defaultOptions, arrayKeyOption, separator, rawNumber);
	}

	enum Param implements Variables.Parameter {

		ARRAY(ARRAY_KEY_PARAM, ARRAY_KEY_ALIAS), SEPARATOR(SEPARATOR_PARAM, SEPARATOR_PARAM_ALIAS),
		NUMBER(NUMBER_RAW_PARAM, NUMBER_RAW_ALIAS);

		private final List<String> keys;

		private Param(String key, String alias) {
			this.keys = List.of(key, alias);
		}

		@Override
		public List<String> keys() {
			return this.keys;
		}

	}

	@Override
	public Formatter formatter() {
		return new JSON5KeyValuesFormatter();
	}

	@Override
	public Optional<KeyValuesMedia> findByMediaType(String mediaType) {
		var json5 = KeyValuesMedia.super.findByMediaType(mediaType);
		if (json5.isPresent()) {
			return json5;
		}
		if (JSON_MEDIA_TYPE.equalsIgnoreCase(mediaType)) {
			return Optional.of(this);
		}
		return Optional.empty();
	}

	@Override
	public boolean hasFileExt(String filename) {
		boolean json5 = KeyValuesMedia.super.hasFileExt(filename);
		if (json5) {
			return true;
		}
		return filename.endsWith("." + JSON_FILE_EXT);
	}

	static enum ArrayKeyOption {

		DUPLICATE(ARRAY_KEY_DUPLICATE_VALUE), //
		ARRAY(ARRAY_KEY_ARRAY_VALUE),;

		private final String value;

		private ArrayKeyOption(String value) {
			this.value = value;
		}

		static ArrayKeyOption defaults() {
			return ArrayKeyOption.DUPLICATE;
		}

		void set(BiConsumer<String, String> consumer) {
			consumer.accept(ARRAY_KEY_PARAM, value);
		}

		static ArrayKeyOption parse(@Nullable String input) {
			if (input == null) {
				return defaults();
			}
			for (var a : ArrayKeyOption.values()) {
				if (a.value.equalsIgnoreCase(input)) {
					return a;
				}
			}
			throw new IllegalArgumentException("Bad ArrayKeyOption: " + input);
		}

	}

	static class JSON5KeyValuesFormatter implements Formatter {

		@Override
		public void format(Appendable appendable, KeyValues kvs) throws IOException {
			JSONObject object = new JSONObject();
			for (var kv : kvs) {
				object.merge(kv.key(), new JSONString(kv.value()));
			}
			String json = JSONStringify.toString(object, 2);
			appendable.append(json);
		}

	}

	static class JSON5KeyValuesParser implements Parser {

		private final JSONParserOptions options;

		private final ArrayKeyOption arrayKeyOption;

		private final String separator;

		private final boolean rawNumber;

		static final JSONParserOptions defaultOptions = new JSONParserOptions.Builder()
			.duplicateBehaviour(DuplicateBehavior.DUPLICATE)
			.build();

		static final JSON5KeyValuesParser parser = new JSON5KeyValuesParser(defaultOptions, ArrayKeyOption.defaults(),
				DEFAULT_SEPARATOR, false);

		public JSON5KeyValuesParser(JSONParserOptions options, ArrayKeyOption arrayKeyOption, String separator,
				boolean rawNumber) {
			super();
			this.options = options;
			this.arrayKeyOption = Objects.requireNonNull(arrayKeyOption);
			this.separator = separator;
			this.rawNumber = rawNumber;
		}

		@Override
		public void parse(InputStream input, BiConsumer<String, String> consumer) throws IOException {
			JSONParser parser = new JSONParser(new InputStreamReader(input, StandardCharsets.UTF_8), options);
			parse(parser, consumer);

		}

		@Override
		public void parse(String input, BiConsumer<String, String> consumer) {
			JSONParser parser = new JSONParser(new StringReader(input), options);
			parse(parser, consumer);
		}

		private void parse(JSONParser parser, BiConsumer<String, String> consumer) {
			JSONValue json = parser.topValueOrNull();
			switch (json) {
				case JSONObject o -> {
					flattenJsonObject(o, "", consumer);
				}
				case JSONArray a -> {
					flattenJsonArray(a, "", consumer);
				}
				case null -> {
				}
				default -> {
					// We ignore top level literals
				}
			}
		}

		private void flattenJsonObject(JSONObject jsonObject, String prefix, BiConsumer<String, String> consumer) {
			for (String key : jsonObject.keySet()) {
				JSONValue value = jsonObject.get(key);
				String newKey = createObjectKey(prefix, key);
				dispatch(newKey, value, consumer);
			}
		}

		private void flattenJsonArray(JSONArray jsonArray, String prefix, BiConsumer<String, String> consumer) {
			for (int i = 0; i < jsonArray.length(); i++) {
				JSONValue value = jsonArray.get(i);
				String newKey = createArrayKey(prefix, i);
				dispatch(newKey, value, consumer);
			}
		}

		private String createObjectKey(String prefix, String key) {
			String newKey = prefix.isEmpty() ? key : prefix + this.separator + key;
			return newKey;
		}

		private String createArrayKey(String prefix, int i) {
			return switch (arrayKeyOption) {
				case ARRAY -> prefix.isEmpty() ? String.valueOf(i) : prefix + "[" + i + "]";
				case DUPLICATE -> prefix.isEmpty() ? String.valueOf(i) : prefix;
			};
		}

		private void dispatch(String newKey, JSONValue value, BiConsumer<String, String> consumer) {
			switch (value) {
				case JSONObject o -> flattenJsonObject(o, newKey, consumer);
				case JSONArray a -> flattenJsonArray(a, newKey, consumer);
				case JSONString s -> consumer.accept(newKey, s.value());
				case JSONBoolean b -> consumer.accept(newKey, String.valueOf(b.val()));
				case JSONNumber n -> consumer.accept(newKey, toString(n));
				case JSONNull n -> {
				}
			}
			;
		}

		private String toString(JSONNumber number) {
			if (rawNumber) {
				return number.raw();
			}
			return String.valueOf(number.value());
		}

	}

}
