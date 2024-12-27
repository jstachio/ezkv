/**
 * EZKV Maven Plugin to load configuration during Maven build and is analogous to
 * <a href="https://www.mojohaus.org/properties-maven-plugin/">properties-maven-plugin</a>
 * except that it will use EZKV to load the properties. Unlike the Codehaus properties
 * plugin this plugin allows chain loading of config.
 * @see io.jstach.ezkv.maven.ReadConfigMojo
 */
@org.jspecify.annotations.NullMarked
package io.jstach.ezkv.maven;