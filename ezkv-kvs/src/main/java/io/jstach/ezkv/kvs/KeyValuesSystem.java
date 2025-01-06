package io.jstach.ezkv.kvs;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import io.jstach.ezkv.kvs.KeyValuesServiceProvider.KeyValuesFilter;
import io.jstach.ezkv.kvs.KeyValuesServiceProvider.KeyValuesLoaderFinder;
import io.jstach.ezkv.kvs.KeyValuesServiceProvider.KeyValuesMediaFinder;

/**
 * The main entry point into the Ezkv configuration library. This interface provides
 * access to core components for loading and processing key-value resources.
 *
 * <p>
 * {@code KeyValuesSystem} allows the creation and configuration of loaders that can
 * retrieve key-value pairs from various resources such as files, classpath locations, and
 * system properties.
 *
 * <p>
 * Example usage:
 * {@snippet :
 * var kvs = KeyValuesSystem.defaults()
 * 	.loader()
 * 	.add("classpath:/start.properties")
 * 	.add("system:///")
 * 	.add("env:///")
 * 	.add("cmd:///-D")
 * 	.load();
 * }
 *
 * @see KeyValuesResource
 * @see KeyValuesLoader
 * @see KeyValuesEnvironment
 * @see KeyValuesServiceProvider
 */
public sealed interface KeyValuesSystem extends AutoCloseable {

	/**
	 * Returns the {@link KeyValuesEnvironment} instance used for system-level
	 * interactions.
	 * @return the environment instance
	 */
	public KeyValuesEnvironment environment();

	/**
	 * Returns a composite {@link KeyValuesLoaderFinder} that aggregates all loader
	 * finders added to the {@link Builder} or found via the {@link ServiceLoader}, if
	 * configured. This composite allows finding loaders that can handle specific
	 * resources by delegating the search to all registered loader finders.
	 * @return the composite loader finder instance
	 */
	public KeyValuesLoaderFinder loaderFinder();

	/**
	 * Returns a composite {@link KeyValuesMediaFinder} that aggregates all media finders
	 * added to the {@link Builder} or found via the {@link ServiceLoader}, if configured.
	 * This composite allows finding media handlers that can handle specific resources by
	 * delegating the search to all registered media finders.
	 * @return the composite media finder instance
	 */
	public KeyValuesMediaFinder mediaFinder();

	/**
	 * Returns a composite {@link KeyValuesFilter} that aggregates all the filters added
	 * to the {@link Builder} or found via the {@link ServiceLoader}, if configured. This
	 * composite allows finding media handlers that can handle specific resources byrs.
	 * @return the composite filter
	 */
	public KeyValuesFilter filter();

	/**
	 * Creates and returns a {@link KeyValuesLoader.Builder} for constructing loaders that
	 * can load key-value pairs from various resources.
	 * @return a new {@link KeyValuesLoader.Builder} instance
	 */
	default KeyValuesLoader.Builder loader() {
		Function<KeyValuesLoader.Builder, KeyValuesLoader> loaderFactory = b -> {
			var env = environment();
			var defaultResource = env.defaultResource();
			var variables = Variables.copyOf(b.variables.stream().map(vf -> vf.apply(env)).toList());
			var sources = b.sources.stream().map(s -> s.apply(env)).toList();
			List<NamedKeyValuesSource> resources = sources.isEmpty() ? List.of(defaultResource) : List.copyOf(sources);
			return DefaultKeyValuesSourceLoader.of(this, variables, resources);
		};
		return new KeyValuesLoader.Builder(loaderFactory);
	}

	/**
	 * This signals to the logger that this key value system will not be used anymore but
	 * there is guarantee of this.
	 */
	@Override
	default void close() {
		environment().getLogger().closed(this);
	}

	/**
	 * Returns a default implementation of {@code KeyValuesSystem} configured with
	 * standard settings and a ServiceLoader based on {@link KeyValuesServiceProvider} and
	 * its classloader.
	 * @return the default {@code KeyValuesSystem} instance
	 */
	public static KeyValuesSystem defaults() {
		return builder().useServiceLoader().build();
	}

	/**
	 * Creates and returns a new {@link Builder} for constructing customized
	 * {@code KeyValuesSystem} instances.
	 * @return a new {@link Builder} instance
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * A builder class for constructing instances of {@link KeyValuesSystem} with
	 * customizable components such as environment, loaders, and media finders.
	 *
	 * <p>
	 * Note: By default, the {@link ServiceLoader} is not enabled. If you wish to include
	 * services discovered via the {@link ServiceLoader}, you need to explicitly set it
	 * using {@link #serviceLoader(ServiceLoader)}.
	 */
	public final static class Builder {

		private @Nullable KeyValuesEnvironment environment;

		private List<KeyValuesLoaderFinder> loadFinders = new ArrayList<>(
				List.of(DefaultKeyValuesLoaderFinder.values()));

		private List<KeyValuesMediaFinder> mediaFinders = new ArrayList<>(List.of(DefaultKeyValuesMedia.values()));

		private List<KeyValuesFilter> filters = new ArrayList<>(List.of(DefaultKeyValuesFilter.values()));

		private @Nullable ServiceLoader<KeyValuesServiceProvider> serviceLoader;

		private Builder() {
		}

		/**
		 * Sets the {@link KeyValuesEnvironment} to be used by the
		 * {@code KeyValuesSystem}.
		 * @param environment the environment to set
		 * @return this builder instance
		 */
		public Builder environment(KeyValuesEnvironment environment) {
			this.environment = environment;
			return this;
		}

		/**
		 * Adds a {@link KeyValuesLoaderFinder} to the list of loader finders.
		 * @param loadFinder the loader finder to add
		 * @return this builder instance
		 */
		public Builder loadFinder(KeyValuesLoaderFinder loadFinder) {
			this.loadFinders.add(loadFinder);
			return this;
		}

		/**
		 * Adds a {@link KeyValuesMediaFinder} to the list of media finders.
		 * @param mediaFinder the media finder to add
		 * @return this builder instance
		 */
		public Builder mediaFinder(KeyValuesMediaFinder mediaFinder) {
			this.mediaFinders.add(mediaFinder);
			return this;
		}

		/**
		 * Adds a filter to the filter chain.
		 * @param filter the filter to add
		 * @return this
		 * @see KeyValuesFilter
		 */
		public Builder filter(KeyValuesFilter filter) {
			this.filters.add(filter);
			return this;
		}

		/**
		 * Sets the {@link ServiceLoader} for loading {@link KeyValuesServiceProvider}
		 * implementations. If this is set, the builder will include any
		 * {@link KeyValuesLoaderFinder} and {@link KeyValuesMediaFinder} instances found
		 * via the {@link ServiceLoader}.
		 *
		 * <p>
		 * Note: This is not enabled by default. You must explicitly set a service loader
		 * to include additional service-provided components.
		 * @param serviceLoader the service loader to set
		 * @return this builder instance
		 */
		public Builder serviceLoader(ServiceLoader<KeyValuesServiceProvider> serviceLoader) {
			this.serviceLoader = serviceLoader;
			return this;
		}

		/**
		 * Uses the default service loader to load extensions.
		 * @return this.
		 */
		public Builder useServiceLoader() {
			return serviceLoader(ServiceLoader.load(KeyValuesServiceProvider.class,
					KeyValuesServiceProvider.class.getClassLoader()));
		}

		/**
		 * Builds and returns a new {@link KeyValuesSystem} instance configured with the
		 * provided settings. The composite loader and media finders will include those
		 * added through this builder and, if specified, those found via the service
		 * loader.
		 * @return a new {@link KeyValuesSystem} instance
		 */
		public KeyValuesSystem build() {
			var environment = this.environment;
			if (environment == null) {
				environment = new DefaultKeyValuesEnvironment();
			}
			/*
			 * We copy as we are about to use the service loader to add more entries and
			 * to avoid mutable issues.
			 */
			var loadFinders = new ArrayList<>(this.loadFinders);
			var mediaFinders = new ArrayList<>(this.mediaFinders);
			var filters = new ArrayList<>(this.filters);
			var serviceLoader = this.serviceLoader;

			if (serviceLoader != null) {
				serviceLoader.forEach(s -> {
					if (s instanceof KeyValuesLoaderFinder rl) {
						loadFinders.add(rl);
					}
					if (s instanceof KeyValuesMediaFinder m) {
						mediaFinders.add(m);
					}
					if (s instanceof KeyValuesFilter f) {
						filters.add(f);
					}
				});
			}
			Comparator<KeyValuesServiceProvider> cmp = Comparator.comparing(KeyValuesServiceProvider::order);
			Collections.sort(loadFinders, cmp);
			Collections.sort(mediaFinders, cmp);
			Collections.sort(filters, cmp);

			KeyValuesLoaderFinder loadFinder = (context, resource) -> {
				return loadFinders.stream().flatMap(rl -> rl.findLoader(context, resource).stream()).findFirst();
			};
			KeyValuesMediaFinder mediaFinder = new CompositeMediaFinder(mediaFinders);
			KeyValuesFilter filter = new CompositeKeyValuesFilter(filters);

			var kvs = new DefaultKeyValuesSystem(environment, loadFinder, mediaFinder, filter);
			kvs.environment().getLogger().init(kvs);
			return kvs;
		}

	}

}

record CompositeKeyValuesFilter(List<KeyValuesFilter> filters) implements KeyValuesFilter {

	@Override
	public Optional<KeyValues> filter(FilterContext context, KeyValues keyValues, Filter filter) {
		boolean found = false;
		for (var f : filters) {
			var opt = f.filter(context, keyValues, filter);
			var kvs = opt.orElse(null);
			if (kvs != null) {
				keyValues = kvs;
				found = true;
			}
		}
		if (found) {
			return Optional.of(keyValues);
		}
		return Optional.empty();
	}
}

/**
 * A composite media finder that aggregates multiple {@link KeyValuesMediaFinder}
 * implementations and searches them in sequence for a matching media type.
 */
record CompositeMediaFinder(List<KeyValuesMediaFinder> finders) implements KeyValuesMediaFinder {
	CompositeMediaFinder {
		finders = List.copyOf(finders);
	}

	@Override
	public Optional<KeyValuesMedia> findByExt(String ext) {
		return finders.stream().flatMap(mf -> mf.findByExt(ext).stream()).findFirst();
	}

	@Override
	public Optional<KeyValuesMedia> findByMediaType(String mediaType) {
		return finders.stream().flatMap(mf -> mf.findByMediaType(mediaType).stream()).findFirst();
	}

	@Override
	public Optional<KeyValuesMedia> findByUri(URI uri) {
		return finders.stream().flatMap(mf -> mf.findByUri(uri).stream()).findFirst();
	}
}

/**
 * A default implementation of {@link KeyValuesSystem}.
 */
record DefaultKeyValuesSystem(KeyValuesEnvironment environment, KeyValuesLoaderFinder loaderFinder,
		KeyValuesMediaFinder mediaFinder, KeyValuesFilter filter) implements KeyValuesSystem {
}
