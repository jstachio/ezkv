package io.jstach.ezkv.kvs;

import static io.jstach.ezkv.kvs.KeyValuesMedia.inputStreamToString;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import io.jstach.ezkv.kvs.KeyValuesMedia.Formatter;
import io.jstach.ezkv.kvs.KeyValuesMedia.Parser;

@SuppressWarnings("EnumOrdinal")
enum DefaultKeyValuesMedia implements KeyValuesMedia, Parser, Formatter {

	PROPERTIES(MEDIA_TYPE_PROPERTIES, FILE_EXT_PROPERTIES) {
		@Override
		public void parse(InputStream is, BiConsumer<String, String> consumer) throws IOException {
			PropertiesParser.readProperties(new InputStreamReader(is, StandardCharsets.UTF_8), consumer);
		}

		@Override
		public void format(Appendable appendable, KeyValues kvs) throws IOException {
			for (var kv : kvs) {
				PropertiesParser.writeProperty(appendable, kv.key(), kv.value());

			}
		}

		@Override
		public Optional<KeyValuesMedia> findByMediaType(String mediaType) {
			if (mediaType.equals("properties")) {
				return Optional.of(this);
			}
			return super.findByMediaType(mediaType);
		}
	},

	URLENCODED(MEDIA_TYPE_URLENCODED, null) {
		@Override
		public void parse(InputStream input, BiConsumer<String, String> consumer) throws IOException {
			String i = inputStreamToString(input);
			parseUriQuery(i, true, consumer);
		}

		@Override
		public void format(Appendable appendable, KeyValues kvs) throws IOException {
			boolean first = true;
			for (var kv : kvs) {
				if (first) {
					first = false;
				}
				else {
					appendable.append('&');
				}
				String key = URLEncoder.encode(kv.key(), StandardCharsets.UTF_8);
				String value = URLEncoder.encode(kv.expanded(), StandardCharsets.UTF_8);
				appendable.append(key).append('=').append(value);
			}

		}

		@Override
		public void parse(String input, BiConsumer<String, String> consumer) {
			parseUriQuery(input, true, consumer);
		}
	}

	;

	private final String mediaType;

	private final @Nullable String fileExt;

	private DefaultKeyValuesMedia(String mediaType, @Nullable String fileExt) {
		this.mediaType = mediaType;
		this.fileExt = fileExt;
	}

	@Override
	public int order() {
		return BUILTIN_ORDER_START + ordinal();
	}

	@Override
	public String getMediaType() {
		return mediaType;
	}

	@Override
	public @Nullable String getFileExt() {
		return fileExt;
	}

	@Override
	public Parser parser() {
		return this;
	}

	@Override
	public Formatter formatter() {
		return this;
	}

	static void parseUriQuery(String query, boolean decode, BiConsumer<String, String> consumer) {
		@SuppressWarnings("StringSplitter")
		String[] pairs = query.split("&");
		for (String pair : pairs) {
			int idx = pair.indexOf("=");
			String key;
			String value;
			if (idx == 0) {
				continue;
			}
			else if (idx < 0) {
				key = pair;
				value = "";
			}
			else {
				key = pair.substring(0, idx);
				value = pair.substring(idx + 1);
			}
			if (decode) {
				key = URLDecoder.decode(key, StandardCharsets.UTF_8);
				value = URLDecoder.decode(value, StandardCharsets.UTF_8);

			}
			if (key.isBlank()) {
				continue;
			}
			consumer.accept(key, value);
		}
	}

	static KeyValues parseURI(URI uri) {
		String query = uri.getRawQuery();
		if (query == null || query.isBlank()) {
			return KeyValues.empty();
		}
		return URLENCODED.parse(query);
	}

	static void parseCSV(String csv, Consumer<String> consumer) {
		Stream.of(csv.split(",")).map(s -> s.trim()).filter(s -> !s.isBlank()).forEach(consumer);
	}

}

/**
 * Writes and parses properties.
 */
final class PropertiesParser {

	private PropertiesParser() {
	}

	/**
	 * Read properties.
	 * @param reader from
	 * @param consumer to
	 * @throws IOException on read failure
	 */
	static void readProperties(Reader reader, BiConsumer<String, String> consumer) throws IOException {
		Properties bp = prepareProperties(consumer);
		bp.load(reader);

	}

	static void writeProperty(Appendable sb, String key, String value) throws IOException {
		writeKeyOrValue(sb, key, true);
		sb.append('=');
		writeKeyOrValue(sb, value, false);
		sb.append('\n');
	}

	private static Properties prepareProperties(BiConsumer<String, String> consumer) throws IOException {

		// Hack to use properties class to load but our map for preserved order
		@SuppressWarnings({ "serial", "nullness" })
		Properties bp = new Properties() {
			@Override
			@SuppressWarnings({ "nullness", "keyfor", "UnsynchronizedOverridesSynchronized" }) // checker
																								// bug
			public @Nullable Object put(Object key, Object value) {
				Objects.requireNonNull(key);
				Objects.requireNonNull(value);
				consumer.accept((String) key, (String) value);
				return null;
			}
		};
		return bp;
	}

	/*
	 * This is inspired by the JDK
	 */
	private static void writeKeyOrValue(Appendable outBuffer, String keyOrValue, boolean escapeSpace)
			throws IOException {
		int len = keyOrValue.length();
		int bufLen = len * 2;
		if (bufLen < 0) {
			bufLen = Integer.MAX_VALUE;
		}
		for (int x = 0; x < len; x++) {
			char aChar = keyOrValue.charAt(x);
			/*
			 * optimization to handle characters not needing special escaping.
			 */
			if ((aChar > 61) && (aChar < 127)) {
				if (aChar == '\\') {
					outBuffer.append('\\');
					outBuffer.append('\\');
					continue;
				}
				outBuffer.append(aChar);
				continue;
			}
			switch (aChar) {
				case ' ':
					if (x == 0 || escapeSpace)
						outBuffer.append('\\');
					outBuffer.append(' ');
					break;
				case '\t':
					outBuffer.append('\\');
					outBuffer.append('t');
					break;
				case '\n':
					outBuffer.append('\\');
					outBuffer.append('n');
					break;
				case '\r':
					outBuffer.append('\\');
					outBuffer.append('r');
					break;
				case '\f':
					outBuffer.append('\\');
					outBuffer.append('f');
					break;
				case '=': // Fall through
				case ':': // Fall through
				case '#': // Fall through
				case '!':
					outBuffer.append('\\');
					outBuffer.append(aChar);
					break;
				default:
					outBuffer.append(aChar);
					break;
			}
		}
	}

}
