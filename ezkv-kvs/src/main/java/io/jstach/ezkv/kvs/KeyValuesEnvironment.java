package io.jstach.ezkv.kvs;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.System.Logger.Level;
import java.net.URI;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.stream.Stream;

import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import io.jstach.ezkv.kvs.KeyValuesEnvironment.Logger;

/**
 * A facade over various system-level singletons used for loading key-value resources.
 * This interface provides a flexible mechanism for accessing and overriding system-level
 * components such as environment variables, system properties, and input streams.
 *
 * <p>
 * Implementations can replace default system behaviors, enabling custom retrieval of
 * environment variables or properties, or integrating custom logging mechanisms.
 *
 * @apiNote The API in this class uses traditional getter methods because the methods are
 * often dynamic and to be consistent with the methods they are facading.
 */
public interface KeyValuesEnvironment {

	// TODO remove
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
		var logger = getLogger();
		logger.warn("Main Args were requested but not provided. Using fallback 'sun.java.command'");
		String value = getSystemProperties().getProperty("sun.java.command");
		if (value == null) {
			throw new IllegalStateException("Cannot get main args from 'sun.java.command' as it was not provided");
		}
		return fallbackMainArgs(value);
	}

	private static @NonNull String[] fallbackMainArgs(String sunJavaCommandValue) {

		// Use fallback by extracting from system property
		String command = sunJavaCommandValue;
		if (!command.isEmpty()) {
			// Split command into components (main class/jar is the first token)
			List<String> components = new ArrayList<>(Arrays.asList(command.split("\\s+")));
			// Remove the first element (main class or jar)
			if (!components.isEmpty()) {
				components.remove(0);
			}
			// Return remaining as the main args
			return components.toArray(new @NonNull String[0]);
		}

		// Return an empty array if no arguments are available
		return new String[0];
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
	 * Retrieves the {@link ResourceLoader} used for loading resources as streams.
	 * @return the resource stream loader instance
	 */
	default ResourceLoader getResourceLoader() {
		return new ResourceLoader() {

			@Override
			public @Nullable InputStream getResourceAsStream(String path) throws IOException {
				return getClassLoader().getResourceAsStream(path);
			}

			@Override
			public Stream<URL> getResources(String path) throws IOException {
				var cl = getClassLoader();
				return Collections.list(cl.getResources(path)).stream();
			}
		};
	}

	/**
	 * Retrieves the {@link FileSystem} used for loading file resources.
	 * @return by default {@link FileSystems#getDefault()}.
	 */
	default FileSystem getFileSystem() {
		return FileSystems.getDefault();
	}

	/**
	 * Gets the current working directory possibly using the passed in filesystem.
	 * @return cwd or {@code null}
	 */
	default @Nullable Path getCWD() {
		return null;
	}

	/**
	 * Retrieves the class loader. By default, delegates to
	 * {@link ClassLoader#getSystemClassLoader()}.
	 * @return the system class loader
	 */
	default ClassLoader getClassLoader() {
		return ClassLoader.getSystemClassLoader();
	}

	/**
	 * Interface for loading resources.
	 */
	public interface ResourceLoader {

		/**
		 * Retrieves an input stream for the specified resource path.
		 * @param path the path of the resource
		 * @return the input stream for the resource, or {@code null} if not found
		 * @throws IOException if an I/O error occurs
		 */
		public @Nullable InputStream getResourceAsStream(String path) throws IOException;

		/**
		 * Retrieves classpath resources basically equilvant to
		 * {@link ClassLoader#getResources(String)}.
		 * @param path see {@link ClassLoader#getResources(String)}.
		 * @return a stream of urls.
		 * @throws IOException if an IO error happens getting the resources URLs.
		 */
		public Stream<URL> getResources(String path) throws IOException;

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
		 * Returns a logger that uses the supplied {@link java.lang.System.Logger}.
		 * <em>Becareful using this because something downstream may need to configure the
		 * system logger based on Ezkv config.</em>
		 * @param logger system logger.
		 * @return logger.
		 */
		public static Logger of(System.Logger logger) {
			return new SystemLogger(logger);
		}

		/**
		 * By default Ezkv does no logging because logging usually needs configuration
		 * loaded first (Ezkv in this case).
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
			debug(KeyValueReference.describe(new StringBuilder("Loading "), resource, true).toString());
		}

		/**
		 * Logs an info message indicating that a resource has been successfully loaded.
		 * @param resource the loaded resource
		 */
		default void loaded(KeyValuesResource resource) {
			info(KeyValueReference.describe(new StringBuilder("Loaded  "), resource, false).toString());
		}

		/**
		 * Logs a debug message indicating that a resource is missing.
		 * @param resource the resource that was not found
		 * @param exception the exception that occurred when the resource was not found
		 */
		default void missing(KeyValuesResource resource, Exception exception) {
			debug(KeyValueReference.describe(new StringBuilder("Missing "), resource, false).toString());
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
	public void missing(KeyValuesResource resource, Exception exception) {
	}

}

final class SystemLogger implements Logger {

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

@SuppressWarnings("ArrayRecordComponent") // TODO We will fix this later.
record DefaultKeyValuesEnvironment(@NonNull String @Nullable [] mainArgs) implements KeyValuesEnvironment {

	@Override
	public @NonNull String[] getMainArgs() {
		var args = mainArgs;
		if (args == null) {
			return KeyValuesEnvironment.super.getMainArgs();
		}
		return args;
	}

}
