package io.jstach.kiwi.kvs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import io.jstach.kiwi.kvs.KeyValuesEnvironment.Logger;

/**
 * A facade over various system-level singletons used for loading key-value resources.
 * This interface provides a flexible mechanism for accessing and overriding system-level
 * components such as environment variables, system properties, and input streams.
 *
 * <p>
 * Implementations can replace default system behaviors, enabling custom retrieval of
 * environment variables or properties, or integrating custom logging mechanisms.
 */
public interface KeyValuesEnvironment {

	/**
	 * If the loader builder is not passed any resources this resource will be used.
	 * @return default resource is <code>classpath:/boot.properties</code>
	 */
	default KeyValuesResource defaultResource() {
		return KeyValuesResource.builder(URI.create("classpath:/boot.properties")).build();
	}

	/**
	 * Retrieves the main method arguments. By default, returns an empty array.
	 * @return an array of main method arguments
	 */
	default @NonNull String[] getMainArgs() {
		return new @NonNull String[] {};
	}

	/**
	 * Retrieves the current system properties. By default, delegates to
	 * {@link System#getProperties()}.
	 * @return the current system properties
	 */
	default Properties getSystemProperties() {
		return System.getProperties();
	}

	/**
	 * Retrieves the current environment variables. By default, delegates to
	 * {@link System#getenv()}.
	 * @return a map of environment variables
	 */
	default Map<String, String> getSystemEnv() {
		return System.getenv();
	}

	/**
	 * Retrieves a random number generator. By default, returns a new {@link Random}
	 * instance.
	 * @return random.
	 */
	default Random getRandom() {
		return new Random();
	}

	/**
	 * Retrieves the standard input stream. By default, delegates to {@link System#in}.
	 * @return the standard input stream or an empty input stream if {@code System.in} is
	 * null
	 */
	default InputStream getStandardInput() {
		InputStream i = System.in;
		return i == null ? InputStream.nullInputStream() : i;
	}

	/**
	 * Retrieves the logger instance used for logging messages. By default, returns a noop
	 * logger.
	 * @return the logger instance
	 */
	default Logger getLogger() {
		return Logger.of();
	}

	/**
	 * Retrieves the {@link ResourceStreamLoader} used for loading resources as streams.
	 * @return the resource stream loader instance
	 */
	default ResourceStreamLoader getResourceStreamLoader() {
		return new ResourceStreamLoader() {

			@Override
			public @Nullable InputStream getResourceAsStream(String path) throws IOException {
				return getClassLoader().getResourceAsStream(path);
			}
		};
	}

	/**
	 * Retrieves the system class loader. By default, delegates to
	 * {@link ClassLoader#getSystemClassLoader()}.
	 * @return the system class loader
	 */
	default ClassLoader getClassLoader() {
		return ClassLoader.getSystemClassLoader();
	}

	/**
	 * Interface for loading resources as input streams.
	 */
	public interface ResourceStreamLoader {

		/**
		 * Retrieves an input stream for the specified resource path.
		 * @param path the path of the resource
		 * @return the input stream for the resource, or {@code null} if not found
		 * @throws IOException if an I/O error occurs
		 */
		public @Nullable InputStream getResourceAsStream(String path) throws IOException;

		/**
		 * Opens an input stream for the specified resource path. Throws a
		 * {@link FileNotFoundException} if the resource is not found.
		 * @param path the path of the resource
		 * @return the input stream for the resource
		 * @throws IOException if an I/O error occurs
		 * @throws FileNotFoundException if the resource is not found
		 */
		default InputStream openStream(String path) throws IOException, FileNotFoundException {
			InputStream s = getResourceAsStream(path);
			if (s == null) {
				throw new FileNotFoundException(path);
			}
			return s;
		}

	}

	/**
	 * Key Values Resource focused logging facade and event capture. Logging level
	 * condition checking is purposely not supplied as these are more like events and many
	 * implementations will replay when the actual logging sytem loads.
	 */
	public interface Logger {

		/**
		 * Returns a logger that uses the supplied {@link System.Logger}. <em>Becareful
		 * using this because something downstream may need to configure the system logger
		 * based on kiwi config.</em>
		 * @param logger system logger.
		 * @return logger.
		 */
		public static Logger of(System.Logger logger) {
			return new SystemLogger(logger);
		}

		/**
		 * By default Kiwi does no logging because logging usually needs configuration
		 * loaded first (kiwi in this case).
		 * @return noop logger.
		 */
		public static Logger of() {
			return NoOpLogger.NOPLOGGER;
		}

		/**
		 * When Key Values System is loaded.
		 * @param system that was just created.
		 */
		default void init(KeyValuesSystem system) {
		}

		/**
		 * Logs a debug-level message.
		 * @param message the message to log
		 */
		public void debug(String message);

		/**
		 * Logs an info-level message.
		 * @param message the message to log
		 */
		public void info(String message);

		/**
		 * Logs an warn-level message.
		 * @param message the message to log
		 */
		public void warn(String message);

		/**
		 * Logs a debug message indicating that a resource is being loaded.
		 * @param resource the resource being loaded
		 */
		default void load(KeyValuesResource resource) {
			debug(DefaultKeyValuesResource.describe(new StringBuilder("Loading "), resource, true).toString());
		}

		/**
		 * Logs an info message indicating that a resource has been successfully loaded.
		 * @param resource the loaded resource
		 */
		default void loaded(KeyValuesResource resource) {
			info(DefaultKeyValuesResource.describe(new StringBuilder("Loaded  "), resource, false).toString());
		}

		/**
		 * Logs a debug message indicating that a resource is missing.
		 * @param resource the resource that was not found
		 * @param exception the exception that occurred when the resource was not found
		 */
		default void missing(KeyValuesResource resource, FileNotFoundException exception) {
			debug(DefaultKeyValuesResource.describe(new StringBuilder("Missing "), resource, false).toString());
		}

		/**
		 * This signals that key values system will no longer be used to load resources
		 * and that some other system can now take over perhaps the logging system.
		 * @param system key value system that was closed.
		 */
		default void closed(KeyValuesSystem system) {
		}

		/**
		 * This is to signal failure that the KeyValueSystem cannot recover from while
		 * attempting to load.
		 * @param exception unrecoverable key values exception
		 */
		default void fatal(Exception exception) {

		}

		/**
		 * Turns a Level into a SLF4J like level String that is all upper case and same
		 * length with right padding. {@link Level#ALL} is "<code>TRACE</code>",
		 * {@link Level#OFF} is "<code>ERROR</code>" and {@link Level#WARNING} is
		 * "<code>WARN</code>".
		 * @param level system logger level.
		 * @return upper case string of level.
		 */
		public static String formatLevel(Level level) {
			return switch (level) {
				case DEBUG -> /*   */ "DEBUG";
				case ALL -> /*     */ "TRACE";
				case ERROR -> /*   */ "ERROR";
				case INFO -> /*    */ "INFO ";
				case OFF -> /*     */ "ERROR";
				case TRACE -> /*   */ "TRACE";
				case WARNING -> /* */ "WARN ";
			};
		}

	}

}

enum NoOpLogger implements Logger {

	NOPLOGGER;

	@Override
	public void debug(String message) {
	}

	@Override
	public void info(String message) {
	}

	@Override
	public void warn(String message) {
	}

	@Override
	public void load(KeyValuesResource resource) {
	}

	@Override
	public void loaded(KeyValuesResource resource) {
	}

	@Override
	public void missing(KeyValuesResource resource, FileNotFoundException exception) {
	}

}

class SystemLogger implements Logger {

	private final System.Logger logger;

	SystemLogger(java.lang.System.Logger logger) {
		super();
		this.logger = logger;
	}

	@Override
	public void debug(String message) {
		logger.log(Level.DEBUG, message);
	}

	@Override
	public void info(String message) {
		logger.log(Level.INFO, message);
	}

	@Override
	public void warn(String message) {
		logger.log(Level.WARNING, message);

	}

}

class DefaultKeyValuesEnvironment implements KeyValuesEnvironment {

}
