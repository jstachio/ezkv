package io.jstach.ezkv.kvs;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Properties;
import java.util.SequencedCollection;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import io.jstach.ezkv.kvs.Variables.Parameters;

/**
 * Represents a mapping function that associates a key to a value, typically used for
 * variable resolution during interpolation.
 * <p>
 * The {@code Variables} interface allows lookup of values based on keys and is used in
 * contexts where key-to-value mappings are required for interpolation. Unlike
 * {@link KeyValues}, there are no duplicate keys in {@code Variables}. <strong> Variables
 * unlike KeyValues may not end up in the final config when loaded and that is why there
 * is a distinction.</strong>. This distinction for example allows you to use environment
 * variables for lookup but not have the final config contain all the environment
 * variables.
 */
@FunctionalInterface
public interface Variables extends Function<String, @Nullable String> {

	/**
	 * Retrieves the value mapped to the specified key.
	 * @param key the key whose value is to be retrieved
	 * @return the value associated with the key, or {@code null} if not found
	 */
	public @Nullable String getValue(String key);

	/**
	 * Applies functional composition to rename key while retrieving value this variables.
	 * @param keyFunc function to rename key.
	 * @return composed variables instance.
	 */
	default Variables renameKey(Function<String, String> keyFunc) {
		return k -> this.getValue(keyFunc.apply(k));
	}

	@Override
	default @Nullable String apply(String t) {
		return getValue(t);
	}

	/**
	 * Find a variable key-value tuple based on vararg keys. This method provides
	 * ergonomics for searching for fallback keys.
	 * @param name names to String. Note if an array passed in is null an NPE will be
	 * thrown but contents of the array (keys) maybe null and are skipped if they are.
	 * @return optional entry where the key is the first matching key and the value is the
	 * value associated with that key.
	 */
	default Optional<Entry<String, String>> findEntry(String... name) {
		for (String k : name) {
			if (k == null)
				continue;
			String prop = getValue(k);
			if (prop != null)
				return Optional.of(Map.entry(k, prop));
		}
		return Optional.empty();
	}

	/**
	 * Find a variable key-value tuple based on vararg keys. This method provides
	 * ergonomics for searching for fallback keys.
	 * @param names to use for keys.
	 * @return optional entry where the key is the first matching key and the value is the
	 * value associated with that key.
	 */
	default Optional<Entry<String, String>> findEntry(SequencedCollection<String> names) {
		for (String k : names) {
			String prop = getValue(k);
			if (prop != null)
				return Optional.of(Map.entry(k, prop));
		}
		return Optional.empty();
	}

	/**
	 * A mixin for enums or similar representing parameters that provides some validation
	 * on retrieving variables.
	 */
	public interface Parameter {

		/**
		 * Keys of the parameter.
		 * @return keys to try.
		 */
		List<String> keys();

		/**
		 * Require a value and transform or throw exception if missing or invalid.
		 * @param <T> transformed type.
		 * @param variables variables to retrieve value from.
		 * @param compute function to transform value.
		 * @return transformed value.
		 * @throws IllegalArgumentException if not found or compute function is invalid.
		 */
		default <T> T require(Variables variables, Function<String, T> compute) throws IllegalArgumentException {
			var entry = entry(variables);
			try {
				return compute.apply(entry.getValue());
			}
			catch (RuntimeException e) {
				throw new IllegalArgumentException("Parameter is invalid. key='%s', value='%s. %s'"
					.formatted(entry.getKey(), entry.getValue(), e.getMessage()), e);
			}
		}

		/**
		 * Require a value and transform or throw exception if missing or invalid.
		 * @param <T> transformed type.
		 * @param variables variables to retrieve value from.
		 * @param compute function to transform value.
		 * @param fallback returned if missing.
		 * @return transformed value.
		 * @throws IllegalArgumentException if not found or compute function is invalid.
		 */
		default <T> T requireElse(Variables variables, Function<String, T> compute, T fallback)
				throws IllegalArgumentException {
			var entry = variables.findEntry(keys()).orElse(null);
			if (entry == null) {
				return fallback;
			}
			try {
				return compute.apply(entry.getValue());
			}
			catch (RuntimeException e) {
				throw new IllegalArgumentException("Parameter is invalid. key='%s', value='%s. %s'"
					.formatted(entry.getKey(), entry.getValue(), e.getMessage()), e);
			}
		}

		/**
		 * Require a value.
		 * @param variables to retrieve value from.
		 * @return value.
		 * @throws IllegalArgumentException if value is missing.
		 */
		default String require(Variables variables) throws IllegalArgumentException {
			return entry(variables).getValue();
		}

		/**
		 * Require a value.
		 * @param variables to retrieve value from.
		 * @param fallback if value is missing from variables return fallback.
		 * @return value.
		 */
		default String requireElse(Variables variables, String fallback) {
			var v = variables.findEntry(keys()).map(e -> e.getValue()).orElse(null);
			return Objects.requireNonNullElse(v, fallback);
		}

		/**
		 * Requires the parameter.
		 * @param variables variables.
		 * @return key and value.
		 */
		default Entry<String, String> entry(Variables variables) {
			var ks = keys();
			Entry<String, String> e = variables.findEntry(ks).orElseThrow(() -> {
				String k = ks.size() == 1 ? ks.get(0) : ks.toString();
				throw new IllegalArgumentException("Parameter is missing. key(s) tried = '" + k + "'");
			});
			return e;
		}

	}

	/**
	 * Represents a specialized {@code Variables} interface that can list all keys it
	 * knows about. The data in {@code Parameters} is guaranteed to be immutable and
	 * copied when created.
	 *
	 * <p>
	 * Unlike {@link KeyValues}, there are no duplicate keys in {@code Parameters}.
	 */
	public sealed interface Parameters extends Variables {

		/**
		 * Returns an iterable over all keys that this {@code Parameters} instance knows
		 * about.
		 * @return an iterable of keys
		 */
		public Iterable<String> keys();

		/**
		 * Iterates over all key-value pairs in this {@code Parameters} instance and
		 * applies them to a provided consumer.
		 * @param consumer the consumer to apply each key-value pair
		 */
		default void forKeyValues(BiConsumer<String, String> consumer) {
			for (var k : keys()) {
				String value = getValue(k);
				if (value == null) {
					continue;
				}
				consumer.accept(k, value);
			}
		}

		/**
		 * Creates a {@code Parameters} instance from a provided map.
		 * @param map the map to create the parameters from
		 * @return a new {@code Parameters} instance
		 */
		public static Parameters of(Map<String, String> map) {
			return new MapParameters(map);
		}

	}

	/**
	 * Returns an empty {@code Parameters} instance.
	 * @return an empty {@code Parameters} instance
	 */
	public static Parameters empty() {
		return EmptyVariables.EMPTY_VARIABLES;
	}

	/**
	 * Creates a new {@code Variables} builder for constructing a composite
	 * {@code Variables} instance.
	 * @return a new {@link Builder} instance
	 */
	public static Variables.Builder builder() {
		return new Builder();
	}

	/**
	 * A builder class for creating composite {@link Variables} instances by combining
	 * multiple sources of key-to-value mappings. <strong> NOTE: Variables resolution
	 * order is the opposite of KeyValues. Primacy takes precedence! </strong>.
	 */
	public final static class Builder implements BiConsumer<String, String> {

		private @Nullable Map<String, String> properties = null;

		private List<Variables> suppliers = new ArrayList<>();

		private Builder() {
		}

		/**
		 * Adds a key-value mapping to this builder.
		 * @param key the key to add
		 * @param value the value associated with the key
		 * @return this builder instance
		 */
		public Builder add(String key, String value) {
			Map<String, String> _properties = properties;
			if (_properties == null) {
				_properties = properties = new LinkedHashMap<>();
			}
			_properties.put(key, value);
			return this;
		}

		@Override
		public void accept(String t, String u) {
			add(t, u);
		}

		/**
		 * Adds a {@link Variables} supplier to this builder.
		 * @param supplier the variables supplier to add
		 * @return this builder instance
		 */
		public Builder add(@Nullable Variables supplier) {
			if (supplier != null) {
				suppliers.add(supplier);
			}
			return this;
		}

		/**
		 * Adds a map of key-value mappings as a {@link Variables} supplier.
		 * @param m the map of key-value mappings to add
		 * @return this builder instance
		 */
		public Builder add(Map<String, String> m) {
			suppliers.add(m::get);
			return this;
		}

		/**
		 * Adds a set of properties as a {@link Variables} supplier.
		 * @param properties the properties to add
		 * @return this builder instance
		 */
		public Builder add(Properties properties) {
			suppliers.add(Variables.create(properties));
			return this;
		}

		/**
		 * Builds a {@link Variables} instance from the current state of this builder,
		 * combining all added sources into a chain.
		 * @return a new {@link Variables} instance
		 */
		public Variables build() {
			List<Variables> candidates = new ArrayList<>(suppliers.size() + 1);
			Map<String, String> p = properties;
			if (p != null && !p.isEmpty()) {
				candidates.add(p::get);
			}
			candidates.addAll(suppliers);
			return copyOf(candidates);
		}

		/**
		 * A {@link Variables} implementation that chains multiple {@code Variables}
		 * sources together, checking each one in sequence for the value of a given key.
		 */
		private static class ChainedVariables implements Variables {

			private Iterable<? extends Variables> variables;

			/**
			 * Constructs a {@code ChainedVariables} instance that checks each
			 * {@code Variables} in sequence for a key's value.
			 * @param variables an iterable of {@code Variables} to chain
			 */
			public ChainedVariables(Iterable<? extends Variables> variables) {
				super();
				this.variables = variables;
			}

			@Override
			public @Nullable String getValue(String name) {
				for (Variables p : variables) {
					String v = p.getValue(name);
					if (v != null)
						return v;
				}
				return null;
			}

			@Override
			public String toString() {
				return "ChainedVariables[" + variables + "]";
			}

		}

	}

	/**
	 * Creates variables from system properties.
	 * @param env environment facade.
	 * @return variables.
	 */
	public static Variables ofSystemProperties(KeyValuesEnvironment env) {
		return k -> env.getSystemProperties().getProperty(k);
	}

	/**
	 * Creates variables from system environment variables.
	 * @param env environment facade.
	 * @return variables.
	 */
	public static Variables ofSystemEnv(KeyValuesEnvironment env) {
		return k -> env.getSystemEnv().get(k);
	}

	/**
	 * Creates a {@code Variables} instance from the provided properties.
	 * @param properties the properties to use as a variable mapping
	 * @return a new {@code Variables} instance
	 */
	public static Variables create(Properties properties) {
		return properties::getProperty;
	}

	/**
	 * Creates a {@code Variables} instance that chains together multiple
	 * {@link Variables} sources.
	 * @param variables an iterable of {@link Variables} to chain
	 * @return a new {@code Variables} instance
	 */
	static Variables create(final SequencedCollection<? extends Variables> variables) {
		return new Builder.ChainedVariables(variables);
	}

	/**
	 * Creates a {@code Variables} instance that chains together multiple
	 * {@link Variables} sources but copies and filters the collection.
	 * @param variables an iterable of {@link Variables} to chain
	 * @return a new {@code Variables} instance
	 */
	public static Variables copyOf(final SequencedCollection<? extends Variables> variables) {
		List<Variables> list = new ArrayList<>();
		for (var v : variables) {
			if (v != empty()) {
				list.add(v);
			}
		}
		if (list.isEmpty()) {
			return empty();
		}
		if (list.size() == 1) {
			return list.get(0);
		}
		return Variables.create(list);
	}

}

enum EmptyVariables implements Parameters {

	EMPTY_VARIABLES;

	@Override
	public @Nullable String getValue(String key) {
		return null;
	}

	@Override
	public Iterable<String> keys() {
		return List.of();
	}

}

record MapParameters(Map<String, String> map) implements Parameters {
	MapParameters {
		map = new LinkedHashMap<>(map);
	}

	@Override
	public @Nullable String getValue(String key) {
		return map.get(key);
	}

	@Override
	@SuppressWarnings("return")
	public Iterable<String> keys() {
		return map.keySet();
	}
}
