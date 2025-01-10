package io.jstach.ezkv.kvs;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.SequencedCollection;
import java.util.SequencedMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.UnaryOperator;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jspecify.annotations.Nullable;

import io.jstach.ezkv.kvs.interpolate.Interpolator;
import io.jstach.ezkv.kvs.interpolate.Interpolator.InterpolationException;
import io.jstach.ezkv.kvs.interpolate.Interpolator.MissingVariableInterpolationException;

/**
 * Represents a collection of {@link KeyValue} entries with various utility methods for
 * manipulation, transformation, and expansion. This interface serves as a central point
 * for managing key-value pairs, allowing operations such as filtering, mapping, and
 * interpolation.
 *
 * <p>
 * KeyValues is basically a glorified {@code Supplier<Stream<KeyValue>>}. If the key
 * values will be streamed multiple times it is recommended to call {@link #memoize()}
 * which will copy the key values if the key values are not already memoized.
 * </p>
 *
 *
 * <h2>Key Features:</h2>
 * <ul>
 * <li>Provides methods for building key-value pairs with {@link Builder}.</li>
 * <li>Supports functional transformations using {@link #map(UnaryOperator)},
 * {@link #filter(Predicate)}, and {@link #flatMap(Function)}.</li>
 * <li>Allows interpolation and expansion of values using variable substitution.</li>
 * <li>Can convert key-values to a {@link Map} representation.</li>
 * </ul>
 *
 * <h2>Usage Example:</h2> The following example demonstrates how to create, expand, and
 * transform key-values:
 *
 * {@snippet :
 * // Create a builder and add key-value pairs
 * KeyValues.Builder builder = KeyValues.builder();
 * builder.add("key1", "value1");
 * builder.add("key2", "${key1}-${key3}");
 *
 * // Build the KeyValues collection
 * KeyValues kvs = builder.build();
 *
 * // Interpolate values using a Variables map
 * Variables variables = Variables.builder().add("key1", "interpolated").add("key3", "value3").build();
 *
 * KeyValues expanded = kvs.expand(variables);
 *
 * // Convert to a map
 * Map<String, String> map = expanded.toMap();
 * System.out.println(map); // {key1=value1, key2=value1-value3}
 * }
 *
 * @see Variables
 * @see KeyValues.Builder
 * @see KeyValues#expand(Variables)
 * @see KeyValues#toMap()
 */
public interface KeyValues extends Iterable<KeyValue> {

	/**
	 * Returns a stream of the contained {@link KeyValue} entries.
	 * @return a {@link Stream} of key-value pairs.
	 */
	public Stream<KeyValue> stream();

	/**
	 * Creates a new {@link Builder} for constructing a {@code KeyValues} instance.
	 * @param resource a {@link KeyValuesResource} defining the source URI and reference
	 * key-value.
	 * @return a new {@link Builder}.
	 */
	public static Builder builder(KeyValuesResource resource) {
		return new Builder(resource.uri(), resource.reference());
	}

	/**
	 * Creates a new {@link Builder} with a default, empty source.
	 * @return a new {@link Builder}.
	 */
	public static Builder builder() {
		return new Builder(KeyValue.Source.NULL_URI, null);
	}

	/**
	 * Returns an empty {@code KeyValues} instance.
	 * @return an empty {@code KeyValues} collection.
	 */
	public static KeyValues empty() {
		return KeyValuesEmpty.EMPTY;
	}

	/**
	 * Creates a {@code KeyValues} instance by copying the given collection.
	 * @param kvs a {@link SequencedCollection} of key-value pairs.
	 * @return a new {@code KeyValues} instance containing the provided key-values.
	 */
	public static KeyValues copyOf(SequencedCollection<KeyValue> kvs) {
		return new ListKeyValues(List.copyOf(kvs));
	}

	/**
	 * Creates a single key values instance.
	 * @param keyValue single key value.
	 * @return keyvalues of one key value.
	 */
	public static KeyValues of(KeyValue keyValue) {
		return new ListKeyValues(List.of(keyValue));
	}

	/**
	 * A builder for constructing {@link KeyValues} instances from key-value pairs.
	 *
	 * <p>
	 * The builder is designed to create key-value pairs with associated metadata,
	 * including flags and source information. It supports adding entries both
	 * individually and from collections, and allows setting flags for all added entries.
	 * </p>
	 *
	 * <h2>Usage Example:</h2> The following example demonstrates how to use the builder:
	 *
	 * {@snippet :
	 * // Create a new builder
	 * KeyValues.Builder builder = KeyValues.builder();
	 *
	 * // Add key-value pairs
	 * builder.add("host", "localhost");
	 * builder.add("port", "8080");
	 * builder.flag(KeyValue.Flag.SENSITIVE);
	 *
	 * // Build the KeyValues instance
	 * KeyValues kvs = builder.build();
	 *
	 * // Convert to a map
	 * Map<String, String> map = kvs.toMap();
	 * System.out.println(map); // Output: {host=localhost, port=8080}
	 * }
	 */
	public final class Builder {

		private final URI source;

		private final @Nullable KeyValue reference;

		private final AtomicInteger index = new AtomicInteger();

		private final List<KeyValue> keyValues = new ArrayList<>();

		private final EnumSet<KeyValue.Flag> flags = EnumSet.noneOf(KeyValue.Flag.class);

		private final boolean noSource;

		/**
		 * Constructs a new {@code Builder} with the specified source URI and reference.
		 * @param source the URI representing the source of these key-values.
		 * @param reference an optional reference {@link KeyValue}, can be {@code null}.
		 */
		private Builder(URI source, @Nullable KeyValue reference) {
			super();
			this.source = source;
			this.reference = reference;
			this.noSource = source.equals(KeyValue.Source.NULL_URI);
		}

		/**
		 * Adds a flag that will be applied to all key-value pairs added by this builder.
		 * @param flag the {@link KeyValue.Flag} to add.
		 * @return this builder, for method chaining.
		 */
		public Builder flag(KeyValue.Flag flag) {
			flags.add(flag);
			return this;
		}

		/**
		 * Adds a key-value pair to the builder.
		 * @param key the key as a {@link String}.
		 * @param value the value as a {@link String}.
		 * @return this builder, for method chaining.
		 */
		public Builder add(String key, String value) {
			keyValues.add(build(key, value));
			return this;
		}

		/**
		 * Adds a key-value pair to the builder from a map entry.
		 * @param e a {@link java.util.Map.Entry} representing the key and value.
		 * @return this builder, for method chaining.
		 */
		public Builder add(Entry<String, String> e) {
			return add(e.getKey(), e.getValue());
		}

		@SuppressWarnings("null") // Eclipse null analysis has some inference issue here
		private static final Comparator<Entry<String, String>> entryComparator = Map.Entry
			.<String, String>comparingByKey()
			.thenComparing(Entry::getValue);

		/**
		 * Adds a collection of entries to the builder. The entries are added in the order
		 * they appear in the collection.
		 * @param entries a {@link SequencedCollection} of entries to add.
		 * @return this builder, for method chaining.
		 */
		public Builder add(SequencedCollection<Entry<String, String>> entries) {
			for (var e : entries) {
				add(e);
			}
			return this;
		}

		/**
		 * Adds a collection of entries to the builder.
		 *
		 * <p>
		 * If the collection is a {@link SequencedCollection}, the entries are added in
		 * the original order of the collection. Otherwise, the entries are first sorted
		 * lexicographically by key and then by value before being added.
		 * </p>
		 * @param entries a {@link Collection} of entries to add.
		 * @return this builder, for method chaining.
		 */
		public Builder add(Collection<Entry<String, String>> entries) {
			switch (entries) {
				case SequencedCollection<Entry<String, String>> sc -> add(sc);
				default -> entries.stream().sorted(entryComparator).forEach(this::add);
			}
			;
			return this;
		}

		/**
		 * Constructs a {@link KeyValue} with the given key and value, including flags and
		 * source information.
		 * @param key the key as a {@link String}.
		 * @param value the value as a {@link String}.
		 * @return a new {@link KeyValue} with the specified key, value, and metadata.
		 */
		public KeyValue build(String key, String value) {
			var s = noSource ? KeyValue.Source.EMPTY : new KeyValue.Source(source, reference, index.incrementAndGet());
			var m = KeyValue.Meta.of(key, value, s, flags);
			return new KeyValue(key, value, m);
		}

		/**
		 * Builds and returns a {@link KeyValues} instance containing all the key-value
		 * pairs added to this builder.
		 * @return a new {@link KeyValues} instance.
		 */
		public KeyValues build() {
			return KeyValues.copyOf(keyValues);
		}

	}

	/**
	 * Collects a stream of {@link KeyValue} into a {@code KeyValues} instance.
	 * @return a {@link Collector} that accumulates key-values into a {@code KeyValues}
	 * object.
	 */
	public static Collector<KeyValue, ?, KeyValues> collector() {
		return Collectors.collectingAndThen(Collectors.toList(), KeyValues::copyOf);
	}

	/**
	 * Collects a stream of {@link java.util.Map.Entry} into a {@code KeyValues} using the
	 * provided builder.
	 * @param builder the {@link Builder} to use for constructing the key-values.
	 * @return a {@link Collector} that accumulates entries into a {@code KeyValues}
	 * object.
	 */
	public static Collector<Map.Entry<String, String>, ?, KeyValues> collector(Builder builder) {
		return Collector.of(() -> builder,
				// Accumulator: add each entry to the builder
				(b, entry) -> b.add(entry),
				// Combiner: combine two builders (can be ignored if the stream is
				// sequential)
				(b1, b2) -> {
					throw new UnsupportedOperationException("Parallel streams not supported");
				},
				// Finisher: call build on the final builder
				Builder::build);
	}

	@Override
	default Iterator<KeyValue> iterator() {
		return stream().iterator();
	}

	/**
	 * Applies a transformation function to each key-value pair in the collection.
	 * @param kv a {@link UnaryOperator} to transform each key-value.
	 * @return a new {@code KeyValues} with transformed entries.
	 */
	default KeyValues map(UnaryOperator<KeyValue> kv) {
		return ToStringableKeyValues.of(() -> stream().map(kv));
	}

	/**
	 * Filters the key-value pairs based on a predicate.
	 * @param predicate a {@link Predicate} to test each key-value.
	 * @return a new {@code KeyValues} with filtered entries.
	 */
	default KeyValues filter(Predicate<KeyValue> predicate) {
		return ToStringableKeyValues.of(() -> stream().filter(predicate));
	}

	/**
	 * This method gets the last element of the key values stream. This ergonomic is
	 * provided because later keys override earlier keys of the same name.
	 * @return optional of the last key value if there is one.
	 */
	default Optional<KeyValue> last() {
		KeyValue last = null;
		for (var kv : this) {
			last = kv;
		}
		return Optional.ofNullable(last);
	}

	/**
	 * Flattens the key-values by applying a mapping function that returns another
	 * {@code KeyValues}.
	 * @param func a function that transforms a {@link KeyValue} into another
	 * {@code KeyValues}.
	 * @return a new flattened {@code KeyValues}.
	 */
	default KeyValues flatMap(Function<KeyValue, KeyValues> func) {
		var f = func.andThen(KeyValues::stream);
		return ToStringableKeyValues.of(() -> stream().flatMap(f));
	}

	/**
	 * Interpolates the values using the provided {@link Variables}. The key value pairs
	 * in <code>this</code> take precedence over the passed in variables such that if
	 * there is a matching key in this and in variables the value of the key value in this
	 * will be used.
	 * @param variables a {@link Variables} map for value substitution.
	 * @return a map of interpolated key-values.
	 * @see #expand(Variables)
	 * @see #toMap()
	 */
	default Map<String, String> interpolate(Variables variables) {
		return expand(variables).toMap();
	}

	/**
	 * Expands the key-values using variable interpolation and return a new key values
	 * where all the value parts of the keys are replaced with the interpolation results.
	 * @param variables a {@link Variables} map for value substitution.
	 * @return a new {@code KeyValues} with expanded values.
	 * @see Variables#empty()
	 */
	default KeyValues expand(Variables variables) {
		return KeyValuesInterpolator.interpolateKeyValues(this, variables, false);
	}

	/**
	 * Returns a memoized version of this {@code KeyValues} which means repeated calls to
	 * iteratore or stream over the KeyValues will always generate the same result.
	 * @return a memoized {@code KeyValues} instance.
	 */
	default KeyValues memoize() {
		if (this instanceof MemoizedKeyValues mkvs) {
			return mkvs;
		}
		return copyOf(stream().toList());
	}

	/**
	 * Redacts sensitive key-value entries by replacing their values.
	 * @return a new {@code KeyValues} with redacted entries.
	 */
	default KeyValues redact() {
		return this.map(KeyValue::redact);
	}

	/**
	 * Converts the key-values to a map where each key maps to its expanded value. The
	 * returned Map is sequenced based on the order of the key-values. <strong>Note this
	 * call does not do any interpolation!</strong> If interpolation is desired it needs
	 * to be done prior with {@link #expand(Variables)} or {@link #interpolate(Variables)}
	 * should be used.
	 * @return a {@link Map} of key-value pairs.
	 */
	default SequencedMap<String, String> toMap() {
		SequencedMap<String, String> m = new LinkedHashMap<>();
		stream().forEach(kv -> m.put(kv.key(), kv.expanded()));
		return m;
	}

	/**
	 * A convenience method to format key values to a String mainly for unit testing to
	 * maintain fluent chain.
	 * @param media which will be used to call {@link KeyValuesMedia#formatter()}
	 * @return formatted key values
	 * @throws UnsupportedOperationException if formatting is not supported by the media.
	 * @see KeyValuesMedia#ofProperties()
	 */
	default String format(KeyValuesMedia media) throws UnsupportedOperationException {
		return media.formatter().format(this);
	}

	// TODO multiMap SequencedMap<String, List<String>>

}

interface ToStringableKeyValues extends KeyValues {

	static KeyValues of(KeyValues kvs) {
		if (kvs instanceof ToStringableKeyValues t) {
			return t;
		}
		return new PrintableKeyValues(kvs);

	}

	static String toString(KeyValues keyValues) {
		StringBuilder sb = new StringBuilder();
		var kvs = keyValues.redact();
		sb.append("KeyValues[").append("\n");
		KeyValuesMedia.ofProperties().formatter().format(sb, kvs);
		sb.append("]").append("\n");
		return sb.toString();
	}

}

interface MemoizedKeyValues extends KeyValues {

}

record PrintableKeyValues(KeyValues values) implements ToStringableKeyValues {
	@Override
	public Stream<KeyValue> stream() {
		return values.stream();
	}

	@Override
	public String toString() {
		return ToStringableKeyValues.toString(this);
	}
}

record ListKeyValues(List<KeyValue> keyValues) implements ToStringableKeyValues, MemoizedKeyValues {

	ListKeyValues {
		keyValues = List.copyOf(keyValues);
	}

	@Override
	public Stream<KeyValue> stream() {
		return keyValues.stream();
	}

	@Override
	public Iterator<KeyValue> iterator() {
		return keyValues.iterator();
	}

	@Override
	public KeyValues memoize() {
		return this;
	}

	@Override
	public Optional<KeyValue> last() {
		if (keyValues.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(keyValues.getLast());
	}

	@Override
	public final String toString() {
		return ToStringableKeyValues.toString(this);
	}
}

class KeyValuesInterpolator {

	static KeyValues interpolateKeyValues(final KeyValues keyValues, final Variables variables, boolean local) {
		// local flag indicates all the key values
		// are from the same resource.
		List<KeyValue> kvs = keyValues.stream().toList();

		final Map<String, KeyValue> flat = new HashMap<>(kvs.size());
		kvs.forEach(kv -> flat.put(kv.key(), kv));

		final Map<String, String> resolved = new LinkedHashMap<>(kvs.size());

		Variables combined = Variables.builder().add(k -> {
			var f = flat.get(k);
			if (f != null) {
				return f.raw();
			}
			return null;

		}).add(resolved).add(variables).build();
		@SuppressWarnings("null")
		Interpolator sub = Interpolator.create((key) -> {
			return combined.getValue(key);
		});
		List<KeyValue> expanded = new ArrayList<>();

		for (KeyValue kv : kvs) {
			KeyValue last = flat.remove(kv.key());

			String value;
			/*
			 * We allow sensitive to be interpolated locally. The assumption here is when
			 * the local flag is passed all the key values passed are from the same
			 * resource.
			 */
			if (kv.isNoInterpolation() || (kv.isSensitive() && !local)) {
				value = kv.value();
			}
			else {
				String v = kv.raw();
				try {
					value = (v.indexOf('$') != -1) ? sub.interpolate(kv.key(), v) : v;
				}
				catch (MissingVariableInterpolationException e) {
					throw e;
				}
				catch (InterpolationException e) {
					// TODO this was old code that may not long be applicable.
					String r = resolved.get(kv.key());
					if (r == null) {
						r = kv.expanded();
					}
					value = r;
				}
			}
			expanded.add(kv.withExpanded(value));
			resolved.put(kv.key(), value);
			if (last != null) {
				flat.put(kv.key(), last);
			}

		}

		return KeyValues.copyOf(expanded);
	}

}

enum KeyValuesEmpty implements KeyValues, ToStringableKeyValues {

	EMPTY;

	@Override
	public Stream<KeyValue> stream() {
		return Stream.empty();
	}

	@Override
	public String toString() {
		return "KeyValues[]";
	}

}