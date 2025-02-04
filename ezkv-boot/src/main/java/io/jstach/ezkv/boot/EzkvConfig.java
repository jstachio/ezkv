package io.jstach.ezkv.boot;

import java.lang.management.ManagementFactory;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.jspecify.annotations.Nullable;

import io.jstach.ezkv.kvs.KeyValue;
import io.jstach.ezkv.kvs.KeyValues;
import io.jstach.ezkv.kvs.KeyValuesEnvironment;
import io.jstach.ezkv.kvs.KeyValuesSystem;
import io.jstach.ezkv.kvs.Variables;
import io.jstach.ezkv.kvs.KeyValuesEnvironment.Logger;

/**
 * Ezkv Config is tiny opinionated configuration framework modeled after Spring Boot that
 * uses Ezkv KVS system to load key values into a Map. It is made to be a slightly better
 * {@link System#getProperties()}.
 * <p>
 * Features are purposely very limited.
 * <p>
 * The loading is as follows:
 * <ol>
 * <li>System.getProperties and System.getEnv are load for interpolation.</li>
 * <li>Resource <code>classpath:/application.properties</code> is loaded (but not
 * required)</li>
 * <li>Resource <code>profile.classpath:/application__PROFILE__.properties</code> is
 * loaded (but not required)</li>
 * </ol>
 * <h2>Example Usage</h2>
 * {@snippet :
 * ezkvConfig.of().getProperty(key); // Use instead of System.getProperty
 * }
 *
 */
public sealed interface EzkvConfig {

	/**
	 * Analogous to {@link System#getProperty(String)}.
	 * @param key to use for lookup for matching value.
	 * @return value or <code>null</code> if not found.
	 */
	@Nullable
	String getProperty(String key);

	/**
	 * Set default properties which will be used in addition to those in the found on
	 * loading. This is analogous to Spring Boots
	 * <code>SpringApplication.setDefaultProperties</code>.
	 * @param properties the additional properties to set
	 */
	@SuppressWarnings("argument")
	public static void setDefaultProperties(Map<String, String> properties) {
		Holder.PROPERTIES = KeyValues.builder().add(properties.entrySet()).build();
	}

	/**
	 * Gets the global config singleton. Analogous to {@link System#getProperties()}.
	 * @return global config that is reloadable.
	 */
	public static ReloadableConfig of() {
		return Holder.Hidden.CONFIG;
	}

	/**
	 * Creates a stable config based on the opinionated loading. Usually {@link #of()} is
	 * the more desirable call.
	 * @return a new loaded stable config.
	 * @see #of()
	 */
	public static StableConfig create() {
		return Holder.load();
	}

	/**
	 * A reloadable config allows reloading but does not guarantee that repeated calls of
	 * {@link #getProperty(String)} and similar will return the same results. Notice that
	 * there is no event propagation or watching of resources. That is am exercise for the
	 * consumer of the library.
	 */
	sealed interface ReloadableConfig extends EzkvConfig {

		/**
		 * The current backing stable config (might be generated).
		 * @return stable config.
		 */
		public StableConfig stableConfig();

		/**
		 * Will reload the config and return the previous config.
		 * @return previous config.
		 */
		public EzkvConfig reload();

	}

	/**
	 * A stable config guarantees that repeated calls of {@link #getProperty(String)} and
	 * similar will return the same result.
	 */
	non-sealed interface StableConfig extends EzkvConfig {

		/**
		 * Describes the key like what resource it came from. Stable Config has this
		 * method because calling describe on reloadable config may generate different
		 * results.
		 * @param key to describe.
		 * @return description.
		 * @throws NoSuchElementException if the key is missing.
		 */
		String describe(String key) throws NoSuchElementException;

		/**
		 * Generates a map from the config.
		 * @return key value map of config.
		 */
		Map<String, String> toMap();

	}

}

class QueueLogger implements Logger {

	private final ConcurrentLinkedQueue<Event> events = new ConcurrentLinkedQueue<>();

	record Event(System.Logger.Level level, String message) {
	}

	@Override
	public void init(KeyValuesSystem system) {
		events.add(new Event(System.Logger.Level.DEBUG, "Initializing"));
	}

	@Override
	public void debug(String message) {
		events.add(new Event(System.Logger.Level.DEBUG, message));

	}

	@Override
	public void info(String message) {
		events.add(new Event(System.Logger.Level.INFO, message));
	}

	@Override
	public void warn(String message) {
		events.add(new Event(System.Logger.Level.WARNING, message));
	}

	@Override
	public void closed(KeyValuesSystem system) {
		System.Logger logger = System.getLogger("io.jstach.ezkv.boot.EzkvConfig");
		Event e;
		while ((e = events.poll()) != null) {
			logger.log(e.level, e.message);
		}
	}

	@Override
	public void fatal(Exception exception) {
		var err = System.err;
		Event e;
		while ((e = events.poll()) != null) {
			err.println("[" + Logger.formatLevel(e.level) + "] io.jstach.ezkv.boot.EzkvConfig - " + e.message);
		}
		err.println("[ERROR] io.jstach.ezkv.boot.EzkvConfig - " + exception.getMessage());
		exception.printStackTrace(err);
	}

}

final class Holder {

	static volatile @Nullable KeyValues PROPERTIES;

	static class Hidden {

		final static EzkvConfig.ReloadableConfig CONFIG = new WrapperEzkvConfig(load());

	}

	static EzkvConfig.StableConfig load() {

		record BootEnvironment(QueueLogger logger) implements KeyValuesEnvironment {
			@Override
			public Logger getLogger() {
				return this.logger;
			}
		}
		var logger = new QueueLogger();
		try (var system = KeyValuesSystem.builder() //
			.useServiceLoader()
			.environment(new BootEnvironment(logger))
			.filter(new OnProfileKeyValuesFilter())
			.addPreFilter("onprofile", "")
			.build()) {
			var properties = Holder.PROPERTIES;

			var loader = system //
				.loader();
			if (properties != null) {
				loader.add("setDefaultProperties", properties);
			}
			var kvs = loader //
				.variables(Variables::ofSystemProperties)
				.variables(Variables::ofSystemEnv)
				.variables(RandomVariables::of)
				.add("classpath:/application.properties", b -> b.name("application").noRequire(true))
				.add("profile.classpath:/application-__PROFILE__.properties", b -> b.name("profiles").noRequire(true))
				.load();
			Map<String, KeyValue> m = new LinkedHashMap<>();
			for (var kv : kvs) {
				m.put(kv.key(), kv);
			}
			var config = new DefaultEzkvConfig("application.properties", m);
			config.setSystemProperties(system);
			return config;
		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}

final class WrapperEzkvConfig implements EzkvConfig.ReloadableConfig {

	volatile EzkvConfig.StableConfig config;

	public WrapperEzkvConfig(EzkvConfig.StableConfig config) {
		super();
		this.config = config;
	}

	@Override
	public @Nullable String getProperty(String key) {
		return config.getProperty(key);
	}

	@Override
	public StableConfig stableConfig() {
		return config;
	}

	@Override
	public EzkvConfig reload() {
		var c = config;
		config = Holder.load();
		return c;

	}

}

final class DefaultEzkvConfig implements EzkvConfig.StableConfig {

	private final String description;

	private final Map<String, KeyValue> keyValues;

	public DefaultEzkvConfig(String description, Map<String, KeyValue> keyValues) {
		super();
		this.description = description;
		this.keyValues = keyValues;
	}

	@Override
	public @Nullable String getProperty(String key) {
		var kv = keyValues.get(key);
		if (kv != null) {
			return kv.expanded();
		}
		return null;
	}

	@Override
	public String describe(String key) {
		return this.description + " " + keyValues.get(key);
	}

	@Override
	public Map<String, String> toMap() {
		return toMap(keyValues);
	}

	private static Map<String, String> toMap(Map<String, KeyValue> kvs) {
		Map<String, String> m = new LinkedHashMap<>();
		for (var e : kvs.entrySet()) {
			m.put(e.getKey(), e.getValue().value());
		}
		return Map.copyOf(m);
	}

	void setSystemProperties(KeyValuesSystem system) {
		var logger = system.environment().getLogger();
		Map<String, String> properties = toMap();

		Map<String, String> propertiesToBeSet = new LinkedHashMap<>();

		final Set<String> commandLineProperties = getJvmCommandLinePropertyNames();
		for (Entry<String, String> e : properties.entrySet()) {
			if (commandLineProperties.contains(e.getKey())) {
				logger.info("Not overriding command line property: " + e.getKey());
			}
			else if (denyPropertyToBeOverridden(e.getKey())) {
				logger.info("Not overriding restricted property: " + e.getKey());
			}
			else {
				if (Objects.isNull(e.getValue())) {
					logger.warn("Property key: " + e.getKey() + " is null");
				}
				else {
					propertiesToBeSet.put(e.getKey(), e.getValue());
				}
			}
		}
		propertiesToBeSet.put("SYSTEM_PROPERTIES_LOADED", "true");
		var systemProperties = system.environment().getSystemProperties();
		for (var e : propertiesToBeSet.entrySet()) {
			systemProperties.setProperty(e.getKey(), e.getValue());
		}

	}

	static boolean denyPropertyToBeOverridden(@Nullable String property) {
		if (property == null)
			return true;

		return (property.startsWith("java.") || property.startsWith("sun.") || property.equals("user.dir")
				|| property.equals("user.name") || property.equals("path.separator") || property.startsWith("os."));
	}

	Set<String> getJvmCommandLinePropertyNames() {
		if (!isJmxModuleAvailable()) {
			return Collections.emptySet();
		}
		try {
			List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
			if (args.isEmpty()) {
				return Collections.emptySet();
			}
			Set<String> props = new HashSet<>(args.size());
			for (String s : args) {
				s = s.trim();
				if (!s.startsWith("-D")) {
					continue;
				}
				int end = s.indexOf("=");
				props.add(s.substring(2, end > 3 ? end : s.length()));
			}
			return props;
		}
		catch (Throwable e) {
			return Collections.emptySet();
		}
	}

	private static boolean isJmxModuleAvailable() {
		ModuleLayer bootLayer = ModuleLayer.boot();
		return bootLayer.findModule("java.management").isPresent();
	}

}
