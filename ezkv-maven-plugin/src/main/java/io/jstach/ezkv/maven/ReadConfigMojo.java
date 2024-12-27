package io.jstach.ezkv.maven;

import java.io.IOException;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.jspecify.annotations.Nullable;

import io.jstach.ezkv.kvs.KeyValuesEnvironment;
import io.jstach.ezkv.kvs.KeyValuesSystem;
import io.jstach.ezkv.kvs.Variables;

/**
 * This plugin is similar to <a href=
 * "https://www.mojohaus.org/properties-maven-plugin/">properties-maven-plugin</a> except
 * that it will use EZKV to load the properties. Unlike the Codehaus properties plugin
 * this plugin allows chain loading of config.
 * <p>
 * However like the Codehaus properties plugin this plugin has the following limitations:
 * </p>
 * <p>
 * This plugin (as all other) is executed in the later phase - when project model is
 * already build in memory.
 * </p>
 * <p>
 * So properties read from external files by this plugin can not by used in project
 * definitions in items like {@code <goal>}, {@code <version>} and so on.
 * </p>
 * <p>
 * Properties read by plugin in one module are not propagated to other modules or child
 * projects.
 * </p>
 * <p>
 * Properties are only available for other plugins in runtime like for
 * maven-resource-plugin for filtering resources.
 * </p>
 */
@Mojo(name = "read-config", defaultPhase = LifecyclePhase.NONE, requiresProject = true, threadSafe = true)
public class ReadConfigMojo extends AbstractMojo {

	@Parameter(defaultValue = "${project}", readonly = true, required = true)
	private @Nullable MavenProject project;

	@Parameter(defaultValue = "${session}", readonly = true, required = true)
	private @Nullable MavenSession session;

	/**
	 * Maven will call this constructor.
	 */
	public ReadConfigMojo() {
	}

	/**
	 * The URLs that will be used when reading properties. These may be non-standard URLs
	 * of the form <code>classpath:com/company/resource.properties</code>. Note that the
	 * type is not <code>URL</code> for this reason and therefore will be explicitly
	 * checked by this Mojo.
	 */
	@Parameter(required = true)
	private String[] urls = new String[0];

	/**
	 * Default scope for test access.
	 * @param urls The URLs to set for tests.
	 */
	public void setUrls(String[] urls) {
		if (urls == null) {
			this.urls = null;
		}
		else {
			this.urls = new String[urls.length];
			System.arraycopy(urls, 0, this.urls, 0, urls.length);
		}
	}

	private static <T> T requireInjected(@Nullable T obj, String message) throws MojoExecutionException {
		if (obj == null) {
			throw new MojoExecutionException("Missing injection '" + message + "'");
		}
		return obj;
	}

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		String[] _urls = this.urls;
		if (_urls == null || _urls.length < 1) {
			throw new MojoExecutionException("urls not set");
		}
		var log = getLog();
		var logger = new MavenLogger(log);
		var session = requireInjected(this.session, "session");
		var project = requireInjected(this.project, "project");
		KeyValuesEnvironment env = new KeyValuesEnvironment() {
			@Override
			public Properties getSystemProperties() {
				return session.getSystemProperties();
			}

			@Override
			public KeyValuesEnvironment.Logger getLogger() {
				return logger;
			}
		};
		var loader = KeyValuesSystem.builder()
			.useServiceLoader() //
			.environment(env) //
			.build()
			.loader();

		final Variables missingSystemPropeties = new Variables() {
			final Set<String> requiredSystemProperties = Collections.singleton("user.home");

			@Override
			public @Nullable String getValue(String key) {
				if (requiredSystemProperties.contains(key)) {
					getLog().info("Using real system property as it was not in maven's system properties. key=" + key);
					return System.getProperty(key);
				}
				return null;
			}
		};
		loader.add(missingSystemPropeties);
		loader.variables(Variables::ofSystemEnv);
		loader.variables(Variables::ofSystemProperties);
		Properties originalProperties = project.getProperties();
		// TODO maybe a bad idea?
		loader.add(originalProperties::getProperty);

		for (String url : _urls) {
			loader.add(url);
		}
		try {
			var kvs = loader.load().memoize();
			if (log.isDebugEnabled()) {
				getLog().debug("Found keys: " + kvs);
			}
			// TODO should we do some sort of lock here?
			// Technically properties is synchronized
			for (var kv : kvs) {
				originalProperties.put(kv.key(), kv.value());
			}

		}
		catch (IOException e) {
			throw new MojoExecutionException("failed to load config", e);
		}

	}

	record MavenLogger(Log log) implements KeyValuesEnvironment.Logger {

		@Override
		public void debug(String message) {
			if (log.isDebugEnabled()) {
				log.debug(message);
			}
		}

		@Override
		public void info(String message) {
			if (log.isInfoEnabled()) {
				log.info(message);
			}
		}

		@Override
		public void warn(String message) {
			if (log.isWarnEnabled()) {
				log.warn(message);
			}
		}

	}

}