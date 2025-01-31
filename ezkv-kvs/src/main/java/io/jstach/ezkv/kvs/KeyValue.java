package io.jstach.ezkv.kvs;

import java.net.URI;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

/**
 * Represents a key-value pair in the Ezkv configuration system. Unlike a simple
 * {@code Map.Entry<String, String>}, this record holds additional metadata that provides
 * more context about the key-value pair, such as:
 *
 * <ul>
 * <li>The original raw value (before any interpolation).</li>
 * <li>The expanded value (after interpolation).</li>
 * <li>Source information indicating where the key-value pair was loaded from.</li>
 * <li>Flags indicating special behavior (e.g., sensitive data or disabling
 * interpolation).</li>
 * </ul>
 *
 * This class is immutable and provides methods for handling sensitive data,
 * interpolation, and chaining resources for further key-value loading.
 *
 * <h2>Example Usage</h2>
 *
 * The following example shows how to load key-values using the Ezkv system:
 *
 * {@snippet :
 *
 * var kvs = KeyValuesSystem.defaults()
 * 	.loader()
 * 	.add("classpath:/start.properties")
 * 	.add("system:///")
 * 	.add("env:///")
 * 	.load();
 *
 * // Accessing key-value pairs:
 * for (KeyValue kv : kvs) {
 * 	System.out.println("Key: " + kv.key() + ", Value: " + kv.value());
 * }
 * }
 *
 * @param key the key associated with this key-value pair.
 * @param expanded the value after any interpolation.
 * @param meta additional metadata associated with this key-value pair.
 */
public record KeyValue(String key, String expanded, Meta meta) {

	/**
	 * Constructs a new {@code KeyValue} with the given key, expanded value, and
	 * associated metadata.
	 * @param key the key associated with this key-value pair (cannot be {@code null}).
	 * @param expanded the value after any interpolation (cannot be {@code null}).
	 * @param meta additional metadata (cannot be {@code null}).
	 */
	public KeyValue {
		Objects.requireNonNull(key);
		Objects.requireNonNull(expanded);
		Objects.requireNonNull(meta);
	}

	/**
	 * Constructs a new {@code KeyValue} with a raw value, using the provided key and raw
	 * value. The {@link Meta} information will be initialized with default settings.
	 * @param key the key associated with this key-value pair.
	 * @param raw the raw (unexpanded) value associated with this key.
	 */
	public KeyValue(String key, String raw) {
		this(key, raw, Meta.of(key, raw, Source.EMPTY, Set.of()));
	}

	public final static String REDACTED_MESSAGE = "REDACTED";

	/**
	 * Gets the expanded value after interpolation.
	 * @return the expanded value.
	 */
	public String value() {
		return this.expanded;
	}

	/**
	 * Gets the original raw value before any interpolation.
	 * @return the raw value.
	 */
	public String raw() {
		return meta().raw();
	}

	/**
	 * Gets the set of flags associated with this key-value pair.
	 * @return the flags as an immutable set.
	 */
	Set<Flag> flags() {
		return meta().flags();
	}

	KeyValue replaceNullSource(URI uri) {
		var original = this.meta.source();
		if (!original.isNullResource()) {
			return this;
		}
		var source = original.withURI(uri);
		var meta = this.meta.withSource(source);
		return new KeyValue(key, expanded, meta);
	}

	/**
	 * Creates a new KeyValue with an updated key.
	 * @param key the new key.
	 * @return new key value.
	 */
	public KeyValue withKey(String key) {
		if (key.equals(this.key))
			return this;
		return new KeyValue(key, expanded, meta);
	}

	/**
	 * Creates a new {@code KeyValue} with an updated expanded value. <strong> NOTE that a
	 * new keyvalue could have this expanded value changed (with a new key value) when
	 * interpolated <em>which happens after each load of a resource</em>. </strong>. If
	 * the value is to be changed permanetly which is usually done by a filter changing
	 * the value of the key (and not the raw value) {@link #withSealedValue(String)}
	 * should be called. The key value will no longer be re-interpolated with the raw
	 * original.
	 * @param expanded the new expanded value.
	 * @return a new {@code KeyValue} with the updated expanded value.
	 */
	public KeyValue withExpanded(String expanded) {
		if (expanded.equals(this.expanded))
			return this;
		return new KeyValue(key, expanded, meta);
	}

	/**
	 * Because EZKV reinterpolates all loaded key values on each resource load to support
	 * chaining the expanded value will changed based on {@link #raw()}. Thus if a filter
	 * or something similar would like to change a value without changing the raw loaded
	 * value this method should be used. Ideally filters should not add values to be
	 * interpolated as that would be confusing and this call prevents that by setting the
	 * {@link Flag#NO_INTERPOLATION}.
	 * @param value expanded value that will not be replaced by interpolation.
	 * @return a new key value.
	 */
	public KeyValue withSealedValue(String value) {
		return addFlags(Set.of(Flag.NO_INTERPOLATION)).withExpanded(value);
	}

	/**
	 * Returns a new {@code KeyValue} instance with its value expanded using the provided
	 * function. The expansion function takes the key as input and returns the expanded
	 * value, or {@code null} if no expansion is necessary. If an expanded value is
	 * provided by the function, a new {@code KeyValue} instance is created with that
	 * expanded value; otherwise, the current instance is returned unchanged.
	 * @param expanded a function that takes the key as input and returns the expanded
	 * value or {@code null} if no expansion is needed
	 * @return a new {@code KeyValue} instance with the expanded value, or the original
	 * instance if no expansion was applied
	 */
	public KeyValue withExpanded(@SuppressWarnings("exports") Function<String, @Nullable String> expanded) {
		String n = key();
		String exp = expanded.apply(n);
		if (exp != null) {
			return withExpanded(exp);
		}
		return this;
	}

	/**
	 * Adds additional flags to this {@code KeyValue} and returns a new instance.
	 * @param flagsCol a collection of flags to add.
	 * @return a new {@code KeyValue} with the added flags.
	 */
	public KeyValue addFlags(Collection<Flag> flagsCol) {
		var flags = EnumSet.noneOf(Flag.class);
		flags.addAll(flags());
		flags.addAll(flagsCol);
		var meta = this.meta.withFlags(flags);
		return new KeyValue(key, expanded, meta);
	}

	/**
	 * Metadata associated with a {@link KeyValue}. This interface encapsulates additional
	 * information about a key-value pair, such as its raw, unexpanded value, its source,
	 * and any flags that modify its behavior.
	 *
	 * <h2>Components:</h2>
	 * <ul>
	 * <li>{@link #raw()} - The original, unexpanded value associated with the key.</li>
	 * <li>{@link #source()} - The source of the key-value, represented by a
	 * {@link KeyValue.Source}.</li>
	 * <li>{@link #flags()} - A set of {@link KeyValue.Flag} that can modify how the
	 * key-value is processed.</li>
	 * </ul>
	 *
	 * <h2>Immutability:</h2> Implementations of {@code Meta} are immutable, ensuring that
	 * once created, the metadata cannot be altered. However, methods are provided to
	 * create modified copies with updated flags or source information.
	 *
	 */
	public sealed interface Meta {

		/**
		 * The original key that was collected on the source.
		 * @return the original key.
		 */
		String originalKey();

		/**
		 * Retrieves the raw, unexpanded value associated with the {@link KeyValue}.
		 * @return the original raw value as a {@link String}.
		 */
		String raw();

		/**
		 * Returns the source from which the key-value pair was loaded.
		 * @return a {@link KeyValue.Source} representing the origin of the key-value.
		 */
		Source source();

		/**
		 * Retrieves any flags associated with the key-value. These flags can alter how
		 * the key-value is interpreted or displayed.
		 * @return a {@link Set} of {@link KeyValue.Flag} representing the flags.
		 */
		Set<Flag> flags();

		/**
		 * Creates a new {@code Meta} instance with the specified flags.
		 * @param flags the collection of flags to include in the new metadata.
		 * @return a new {@code Meta} instance with the updated flags.
		 */
		Meta withFlags(Collection<Flag> flags);

		/**
		 * Creates a new {@code Meta} instance with the specified source.
		 * @param source the new source to associate with the metadata.
		 * @return a new {@code Meta} instance with the updated source.
		 */
		Meta withSource(Source source);

		/**
		 * Factory method to create a new {@code Meta} instance with the specified raw
		 * value, source, and flags.
		 * @param originalKey original key.
		 * @param raw the raw value associated with the key-value.
		 * @param source the source from which the key-value was loaded.
		 * @param flags the flags associated with the key-value.
		 * @return a new {@code Meta} instance.
		 */
		static Meta of(String originalKey, String raw, Source source, Set<Flag> flags) {
			return new DefaultMeta(originalKey, raw, source, flags);
		}

	}

	record DefaultMeta(String originalKey, String raw, Source source, Set<Flag> flags) implements Meta {
		public DefaultMeta {
			flags = FlagSet.copyOf(flags, Flag.class);
		}

		@Override
		public Meta withFlags(Collection<Flag> flags) {
			var flagsCopy = FlagSet.copyOf(flags, Flag.class);
			return new DefaultMeta(originalKey, raw, source, flagsCopy);
		}

		@Override
		public Meta withSource(Source source) {
			return new DefaultMeta(originalKey, raw, source, flags);
		}

		@Override
		public String toString() {
			return "Meta[raw=" + raw + ", source=" + Source.toString(source) + ", flags=" + flags + "]";
		}

	}

	/**
	 * Represents the source information for a {@link KeyValue}. A {@code Source} can
	 * indicate where the key-value pair was loaded from, such as a configuration file,
	 * system properties, or environment variables. It also supports linking a key-value
	 * pair to another one through the {@code reference} field.
	 *
	 * <h2>Fields:</h2>
	 * <ul>
	 * <li>{@code uri} - The URI representing the origin of the key-value pair.</li>
	 * <li>{@code reference} - An optional {@link KeyValue} that this key-value depends on
	 * (e.g., for chained loading).</li>
	 * <li>{@code index} - A numerical identifier that can be used for ordering or
	 * tracking.</li>
	 * </ul>
	 *
	 * <h2>Usage Example:</h2>
	 *
	 * The following example demonstrates creating a source from a URI:
	 *
	 * {@snippet :
	 * URI fileUri = URI.create("file:///config.properties");
	 * KeyValue.Source source = new KeyValue.Source(fileUri, null, 0);
	 * System.out.println("Source URI: " + source.uri());
	 * }
	 *
	 * @param uri the URI representing the origin of the key-value pair.
	 * @param reference an optional key-value pair that this one references.
	 * @param index an optional index for ordering.
	 */
	public record Source(URI uri, @Nullable KeyValue reference,
			int index) implements KeyValueReference, KeyValuesSource {

		/**
		 * A constant representing a "null" URI for cases where the source is not
		 * specified.
		 */
		public static URI NULL_URI = URI.create("null:///");

		/**
		 * A default, empty source instance used when no specific source information is
		 * available.
		 */
		public static Source EMPTY = new Source();

		/**
		 * Creates an empty source.
		 * @see #EMPTY
		 */
		public Source() {
			this(NULL_URI, null, 0);
		}

		/**
		 * Checks if this {@code Source} is considered a null resource.
		 * @return {@code true} if the source URI is {@link #NULL_URI}, otherwise
		 * {@code false}.
		 */
		boolean isNullResource() {
			return uri.equals(NULL_URI);
		}

		/**
		 * Creates a new {@code Source} instance with the specified URI, keeping the other
		 * fields the same.
		 * @param uri the new URI to set.
		 * @return a new {@code Source} with the updated URI.
		 */
		Source withURI(URI uri) {
			return new Source(uri, reference, index);
		}

		/**
		 * Redacts the source.
		 * @return redacted source.
		 */
		Source redact() {
			if (!KeyValueReference.isRedacted(this)) {
				return this;
			}
			var uri = URI.create(KeyValueReference.redactURI(this, KeyValue.REDACTED_MESSAGE));
			return new Source(uri, reference, index);
		}

		@Override
		public String toString() {
			return toString(redact());
		}

		private static String toString(Source source) {
			if (source.isNullResource()) {
				return "Source[empty]";
			}
			return "Source[uri=" + source.uri
					+ (source.reference == null ? "" : ", reference=" + KeyValueReference.toStringRef(source.reference))
					+ ", index=" + source.index + "]";
		}

	}

	/**
	 * Enumeration representing flags that can modify the behavior of a {@link KeyValue}.
	 * These flags provide additional metadata that can influence how key-value pairs are
	 * processed, stored, or displayed.
	 *
	 * <h2>Flags:</h2>
	 * <ul>
	 * <li>{@link #NO_INTERPOLATION} - Indicates that the key-value should not be
	 * interpolated, meaning its value should not undergo any variable substitution.</li>
	 * <li>{@link #SENSITIVE} - Marks the key-value as containing sensitive information,
	 * such as passwords or secrets, which may be redacted when displayed.</li>
	 * </ul>
	 */
	public enum Flag {

		/**
		 * Indicates that the key-value pair should not undergo any interpolation or
		 * variable substitution. Useful for cases where the raw value should be preserved
		 * as-is.
		 */
		NO_INTERPOLATION,

		/**
		 * Marks the key-value as containing sensitive information. When this flag is set,
		 * the value may be redacted when displayed to avoid leaking secrets. <strong>It
		 * also means it cannot be used for interpolation with the exception of it being
		 * allowed locally in the same resource!</strong>
		 */
		SENSITIVE;

		/**
		 * Checks if this flag is set for the given {@link KeyValue}.
		 * @param kv the key-value pair to check.
		 * @return {@code true} if the flag is set on the provided key-value, otherwise
		 * {@code false}.
		 */
		public boolean isSet(KeyValue kv) {
			return kv.isFlag(NO_INTERPOLATION);
		}

	}

	/**
	 * Checks if the {@link Flag#NO_INTERPOLATION} flag is set on this {@code KeyValue}.
	 * This indicates that the value should not undergo any interpolation or variable
	 * substitution.
	 * @return {@code true} if the {@code NO_INTERPOLATION} flag is set, otherwise
	 * {@code false}.
	 */
	public boolean isNoInterpolation() {
		return isFlag(Flag.NO_INTERPOLATION);
	}

	/**
	 * Checks if the {@link Flag#SENSITIVE} flag is set on this {@code KeyValue}. A
	 * sensitive value is marked to indicate that it should be treated as confidential and
	 * may be redacted when displayed to prevent leaking sensitive information. <strong>It
	 * also means it cannot be used for interpolation with the exception of it being
	 * allowed locally in the same resource!</strong>
	 * @return {@code true} if the {@code SENSITIVE} flag is set, otherwise {@code false}.
	 */
	public boolean isSensitive() {
		return isFlag(Flag.SENSITIVE);
	}

	/**
	 * Checks if a specific flag is set on this {@code KeyValue}.
	 * @param flag the flag to check
	 * @return {@code true} if the specified flag is set, otherwise {@code false}.
	 */
	public boolean isFlag(Flag flag) {
		return meta().flags().contains(flag);
	}

	/**
	 * Redacts the sensitive value in this key-value pair by replacing it with a default
	 * message.
	 * @return a new {@code KeyValue} with the sensitive data redacted.
	 */
	public KeyValue redact() {
		return redact(REDACTED_MESSAGE);
	}

	/**
	 * Redacts the sensitive value in this key-value pair by replacing it with a custom
	 * message.
	 * @param redactMessage the message to use for redaction.
	 * @return a new {@code KeyValue} with the sensitive data redacted.
	 */
	public KeyValue redact(String redactMessage) {
		if (isSensitive()) {
			String expanded = redactMessage;
			String raw = redactMessage;
			var flags = EnumSet.copyOf(this.meta.flags());
			flags.remove(Flag.SENSITIVE);
			var source = meta.source();
			var originalKey = meta.originalKey();
			Meta meta = new DefaultMeta(originalKey, raw, source, flags);
			return new KeyValue(key, expanded, meta);
		}
		return this;
	}

	boolean isOriginalKey() {
		return key.equals(meta.originalKey());
	}

	@Override
	public String toString() {
		return toString(redact());
	}

	private static String toString(KeyValue kv) {
		return "KeyValue[key='" + kv.key + "'" //
				+ (kv.isOriginalKey() ? "" : ", originalKey='" + kv.meta().originalKey() + "'") //
				+ ", raw='" + kv.raw() + "'" //
				+ ", expanded='" + kv.expanded + "'"//
				+ ", source=" + Source.toString(kv.meta().source())//
				+ (kv.flags().isEmpty() ? "" : ", flags=" + kv.flags()) //
				+ "]";
	}

}
