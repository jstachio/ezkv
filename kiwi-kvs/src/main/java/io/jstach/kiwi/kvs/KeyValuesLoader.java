package io.jstach.kiwi.kvs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import io.jstach.kiwi.kvs.interpolate.Interpolator.InterpolationException;

/**
 * Represents a loader responsible for loading {@link KeyValues} from configured sources.
 * This interface defines the contract for loading key-value pairs, potentially involving
 * interpolation or resource chaining.
 *
 * <p>
 * Implementations of this interface may load key-values from various sources such as
 * files, classpath resources, or system properties.
 *
 * @see KeyValues
 * @see Variables
 */
public interface KeyValuesLoader {

	/**
	 * Loads key-values from configured sources.
	 * @return a {@link KeyValues} instance containing the loaded key-value pairs
	 * @throws IOException if an I/O error occurs during loading
	 * @throws FileNotFoundException if a specified resource is not found
	 * @throws InterpolationException if an error occurs during value interpolation
	 */
	public KeyValues load() throws IOException, FileNotFoundException, InterpolationException;

	/**
	 * A builder class for constructing instances of {@link KeyValuesLoader}. The builder
	 * allows adding multiple sources from which key-values will be loaded, as well as
	 * setting variables for interpolation.
	 */
	public class Builder implements KeyValuesLoader {

		private final Supplier<KeyValuesResource> defaultResource;

		private final Function<Variables, KeyValuesSourceLoader> loaderFactory;

		private final List<KeyValuesSource> sources = new ArrayList<>();

		private Variables variables = Variables.empty();

		/**
		 * Constructs a new {@code Builder} with a specified loader factory.
		 * @param defaultResource default resource if add is never called.
		 * @param loaderFactory a function that creates a {@link KeyValuesSourceLoader}
		 * based on provided variables
		 */
		Builder(Supplier<KeyValuesResource> defaultResource, Function<Variables, KeyValuesSourceLoader> loaderFactory) {
			super();
			this.defaultResource = defaultResource;
			this.loaderFactory = loaderFactory;
		}

		/**
		 * Adds a {@link KeyValuesResource} as a source to the loader.
		 * @param resource the resource to add
		 * @return this builder instance
		 */
		public Builder add(KeyValuesResource resource) {
			sources.add(resource);
			return this;
		}

		/**
		 * Adds a named {@link KeyValues} source to the loader.
		 * @param name the name of the source
		 * @param keyValues the key-values to add
		 * @return this builder instance
		 */
		public Builder add(String name, KeyValues keyValues) {
			sources.add(new NamedKeyValues(name, keyValues));
			return this;
		}

		/**
		 * Adds a {@link URI} as a source by wrapping it in a {@link KeyValuesResource}.
		 * @param uri the URI to add
		 * @return this builder instance
		 */
		public Builder add(URI uri) {
			return add(KeyValuesResource.builder(uri).build());
		}

		/**
		 * Adds a URI specified as a string as a source to the loader.
		 * @param uri the URI string to add
		 * @return this builder instance
		 */
		public Builder add(String uri) {
			return add(URI.create(uri));
		}

		/**
		 * Sets the {@link Variables} used for interpolation when loading key-values.
		 * @param variables the variables to use for interpolation
		 * @return this builder instance
		 */
		public Builder variables(Variables variables) {
			this.variables = variables;
			return this;
		}

		/**
		 * Builds and returns a new {@link KeyValuesLoader} based on the current state of
		 * the builder.
		 *
		 * <p>
		 * If no sources are specified, a default classpath resource
		 * {@code classpath:/system.properties} is used.
		 * @return a new {@link KeyValuesLoader} instance
		 */
		public KeyValuesLoader build() {
			var resources = this.sources.isEmpty() ? List.of(defaultResource.get()) : List.copyOf(this.sources);
			return () -> loaderFactory.apply(variables).load(resources);
		}

		/**
		 * Loads key-values using the current builder configuration.
		 * @return a {@link KeyValues} instance containing the loaded key-value pairs
		 * @throws IOException if an I/O error occurs during loading
		 * @throws FileNotFoundException if a specified resource is not found
		 */
		@Override
		public KeyValues load() throws IOException, FileNotFoundException {
			return build().load();
		}

	}

}
