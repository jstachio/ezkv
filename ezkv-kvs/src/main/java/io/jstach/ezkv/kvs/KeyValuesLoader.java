package io.jstach.ezkv.kvs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;

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
	 * @throws FileNotFoundException if a specified resource is not found (old io)
	 * @throws NoSuchFileException if a specified resource is not found (nio)
	 * @throws KeyValuesException if an error occurs while processing keys such as
	 * interpolation or invalid resource keys.
	 * @throws UncheckedIOException if an IO error happens that had to be wrapped. Some
	 * loaders may throw an unchecked IO because of the difficulty of checked exceptions.
	 * The KeyValues system will unwrap the exception and check if it is one of the
	 * previous missing resource exceptions.
	 */
	public KeyValues load()
			throws IOException, FileNotFoundException, NoSuchFileException, KeyValuesException, UncheckedIOException;

	/**
	 * A builder class for constructing instances of {@link KeyValuesLoader}. The builder
	 * allows adding multiple sources from which key-values will be loaded, as well as
	 * setting variables for interpolation. <strong>Note that the order of the "add" and
	 * {@link #variables} methods does matter.</strong>
	 *
	 * @apiNote The creation of the builder is currently encapsulated at the moment and is
	 * done by {@link KeyValuesSystem#loader}.
	 * @see KeyValuesSystem#loader()
	 */
	public final class Builder implements KeyValuesLoader {

		final Function<Builder, KeyValuesLoader> loaderFactory;

		final List<Function<KeyValuesEnvironment, ? extends NamedKeyValuesSource>> sources = new ArrayList<>();

		final List<Function<KeyValuesEnvironment, ? extends Variables>> variables = new ArrayList<>();

		private int resourceCount = 0;

		private String namePrefix = "root";

		Builder(Function<Builder, KeyValuesLoader> loaderFactory) {
			super();
			this.loaderFactory = loaderFactory;
		}

		/**
		 * Adds a {@link KeyValuesResource} as a source to the loader.
		 * @param resource the resource to add
		 * @return this builder instance
		 */
		public Builder add(KeyValuesResource resource) {
			sources.add(e -> resource);
			resourceCount++;
			return this;
		}

		/**
		 * Add resource using callback on builder.
		 * @param uri uri of resource
		 * @param builder builder to add additional properties.
		 * @return this.
		 */
		public Builder add(String uri, Consumer<KeyValuesResource.Builder> builder) {
			var b = KeyValuesResource.builder(uri);
			builder.accept(b);
			return add(b.build());
		}

		/**
		 * Adds a named {@link KeyValues} source to the loader.
		 * @param name the name of the source
		 * @param keyValues the key-values to add
		 * @return this builder instance
		 */
		public Builder add(String name, KeyValues keyValues) {
			sources.add(e -> new NamedKeyValues(name, keyValues));
			return this;
		}

		/**
		 * Adds a {@link URI} as a source by wrapping it in a {@link KeyValuesResource}.
		 * The name of the resource will be automatically generated based on
		 * {@link #namePrefix(String)} and a counter.
		 * @param uri the URI to add
		 * @return this builder instance
		 */
		public Builder add(URI uri) {
			return add(KeyValuesResource.builder(uri).name(namePrefix + resourceCount).build());
		}

		/**
		 * Adds a URI specified as a string as a source to the loader. The name of the
		 * resource will be automatically generated based on {@link #namePrefix(String)}
		 * and a counter.
		 * @param uri the URI string to add
		 * @return this builder instance
		 */
		public Builder add(String uri) {
			return add(URI.create(uri));
		}

		/**
		 * Adds {@link Variables} for interpolation when loading key-values.
		 * @param variables the variables to use for interpolation
		 * @return this builder instance
		 */
		public Builder add(Variables variables) {
			this.variables.add(e -> variables);
			return this;
		}

		/**
		 * Adds variables that will be resolved based on the environment. <strong>
		 * Variables resolution order is the opposite of KeyValues. Primacy takes
		 * precedence! </strong> This is useful if you want to use environment things for
		 * variables that are bound to {@link KeyValuesEnvironment}. This is preferred
		 * instead of just creating Variables from {@link System#getProperties()} or
		 * {@link System#getenv()} directly.
		 * @param variablesFactory function to create variables from environment.
		 * @return this
		 * @see Variables#ofSystemProperties(KeyValuesEnvironment)
		 * @see Variables#ofSystemEnv(KeyValuesEnvironment)
		 * @see #add(Variables)
		 */
		public Builder variables(Function<KeyValuesEnvironment, Variables> variablesFactory) {
			this.variables.add(variablesFactory);
			return this;
		}

		/**
		 * Adds resource that will be resolved based on the environment.
		 * @param resourceFactory function to create resource from environment.
		 * @return this
		 * @see #add(KeyValuesResource)
		 */
		public Builder resource(Function<KeyValuesEnvironment, KeyValuesResource> resourceFactory) {
			this.sources.add(resourceFactory);
			return this;
		}

		/**
		 * Sets the resource name prefix for auto naming of resources based on a counter.
		 * This is to support the add methods that do not specify a resource name.
		 * @param namePrefix must follow {@value KeyValuesResource#RESOURCE_NAME_REGEX}
		 * @return by default <code>root</code>.
		 */
		public Builder namePrefix(String namePrefix) {
			this.namePrefix = KeyValuesSource.validateName(namePrefix);
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
			return loaderFactory.apply(this);
		}

		/**
		 * Loads key-values using the current builder configuration.
		 * @return a {@link KeyValues} instance containing the loaded key-value pairs
		 * @throws IOException if an I/O error occurs during loading
		 * @throws FileNotFoundException if a specified resource is not found. This
		 * exception gets special treatment if thrown if the resource is optional it will
		 * not be an error.
		 * @throws NoSuchFileException if a specified resource is not found. This
		 * exception gets special treatment if thrown if the resource is optional it will
		 * not be an error.
		 */
		@Override
		public KeyValues load() throws IOException, FileNotFoundException, NoSuchFileException {
			return build().load();
		}

	}

}
