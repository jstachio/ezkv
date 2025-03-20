[![Github](https://github.com/jstachio/ezkv/actions/workflows/maven.yml/badge.svg)](https://github.com/jstachio/ezkv/actions)

<img src="etc/ezkv_logo.svg" alt="EZKV (pronounced ee-zee kee-vee) logo">

<sub><sup>*(pronounced "ee-zee kee-vee")*</sup></sub>

# Ezkv

A non-opinionated Java *bootstrapping configuration* library
that allows recursive chain loading of configuration from key values.


*"Ezkv lemon squeezy!"*

**Key values are everywhere** 
(also known as an associative arrays, list of tuples, or name value pairs)!

Environment variables, System properties, cloud meta data, vault,
HTTP FORM post, URI queries, command line arguments
even most forms of JSON, HOCON, TOML YAML and XML
can all be represented as simple *"key value"* pairs.

Thus it is the perfect common denominator for providing applications with initial configuration.
We call this *"bootstrapping configuration"* and it is usually gathered even before logging.

To do this Ezkv loads streams of key values (KeyValue) from resources. 
What is unique about it is that certain key values will load more key values to the current
stream which is what we call chaining.

    URI -> KeyValues* -> URI* -> KeyValues -> ...

Streams are useful because they can be filtered and transformed
which matters because keys and values from various sources often need transformation. 
For example environment variable names often need to be converted to lower case
and some prefix removed.

**In short it is a micro configuration framework that itself can be configured with key values.**

## Documentation

* **[Latest SNAPSHOT Ezkv doc](https://jstach.io/ezkv/)**
* **[Current released Ezkv doc](https://jstach.io/doc/ezkv/current/apidocs)**

> [!WARNING]
> While this readme does contain some documentation the above is the preferred documentation and 
more likely to be up to date and correct!
> It is very likely the below documentation is not correct is only provided for indexing.

## Why and Use Case

Ezkv allows **"users"** to decide where their configuration comes from instead of developers. 

A simple command line application use case might be like [ripgrep](https://github.com/BurntSushi/ripgrep) where a single
environment variable dictates the location of configuration. Ezkv easily supports
this style as well as allow the user to then load additional configuration from
other places using key values in the configuration file. Ezkv can even allow configuration to come from other environment variables which is something not really possible with ripgrep or other similar tools.

Another use case might be to simulate Spring Boot's configuration loading but without all of Spring Boot. Ezkv can do that declaratively through key values. In fact a user could make the previously mentioned ripgrep environment variable do a Spring Boot like load of configuration.

Ezkv is not a property lookup or configuration binding (mapping to user defined types) framework.
Its only job is to find key values. Because of this it can be used with any other downstream configuration 
frameworks as a source including Spring, or J2EE applications!

## Example

A simple example using `java.util.Properties` files that could be parsed to `KeyValues` would be:


```java
var kvs = KeyValuesSystem.defaults()
  .loader()
  .variables(Variables::ofSystemProperties) // Use system properties as base variables for interpolation
  .add("classpath:/start.properties")
  .add("system:///") // add system properties to override
  .add("env:///")    // add env variables to override
  .add("cmd:///?_filter_sed=s/^-D//")  // add command line for final override using the prefix of -D
  .load();

// give the key values to some other config framework like Spring:
var map = kvs.toMap();
ConfigurableEnvironment env = applicationContext.getEnvironment();
env.getPropertySources().addFirst(new MapPropertySource("start", map));
```

**start.properties** (first loaded):

```properties
message=Hello ${user.name}
_load_foo=classpath:/foo.properties
port.prefix=1
```

(take note of the `_load_foo` key)

**foo.properties loaded** (second loaded resource):


```properties

user.name=Barf
message=Merchandising
db.port=${port.prefix}5672
_load_user=file:/${user.home}/.config/myapp/user.properties
_flags_user=sensitive,no_require
```
(take note of the `_load_user` and `_flags_user` key)

**user.properties** (third loaded resource treated as sensitive)

```properties
secret=12345 # my luggage combination
port.prefix=3
```

After that system properties, then environment variables and then command line key values are added. 

Ignoring environment variables and system properties our effective key values printed out will be:

```properties
message=Merchandising
user.name=Barf
port.prefix=3
db.port=35672
secret=REDACTED
```


Let us go back to the original code. It should be pretty
obvious now that the same thing can be done like: 

```java
var kvs = KeyValuesSystem.defaults()
  .loader()
  .add("classpath:/start.properties")
  .load();
```

With `start.properties` having the additional key values:

```properties
message=Hello ${user.name}
port.prefix=1
_load_foo=classpath:/foo.properties
_load_system=system:///
_load_env=env:///
_load_cmd=cmd:///?_filter_sed=s/^-D//
```

(If you don't like the syntax of the special loading keys that is indeed configurable... through key values of course.)

## Ezkv is NOT a `System.getProperty` or other config framework replacement

Most configuration frameworks are focused on *"binding"*, dependency injection, or ergonomics on a
`Map<String,String>`. They are focused on transforming the flat key values to objects. Ezkv does not do that. 
**These libraries often have a very opinionated loading scheme**
and often the only way to configure that is through code. 

Ezkv is lower level than most config libraries but yet allows the configuration to happen in configuration. 
It is mostly concerned with loading and because of its zero dependency and no logging architecture it can be 
used very early to provide other early init libraries with a `Map<String,String>`
(or the complete stream of key values found). That is why there is not really a `getProperty(key)`
like method provided by `ezkv-kvs`. That is for configuration frameworks downstream.

In fact Ezkv rather just fill `System.getProperties` from loaded resources so that
you do not have to use another library for configuration lookup. That is for retrieval 
a singleton like `System.getProperties` is often good enough for simple applications. 

Yes Ezkv is very simple but [simple is good](https://www.infoq.com/presentations/Simple-Made-Easy/).

That being said if you need a System.getProperty like replacement Ezkv does provide an
opinionated module.

### Ezkv Boot 

While the Ezkv KVS core does not provide an opinion on loading Ezkv does provide a separate opinionated
module (ezkv-boot) that more or less mimics Spring Boot's loading of configuration without requiring Spring Boot.

This module also serves another purpose in that it is example code of using ezkv-kvs you can copy and customize 
for your own applications/libraries.

### Ezkv Maven plugin

The Ezkv maven plugin is a far more capable replacement of the [Codehaus Properties Maven plugin](https://www.mojohaus.org/properties-maven-plugin/).
This allows you to use much of the same configuration you would use in your application during the build process.
This is particularly useful for database code generator plugins like [jOOQ](https://www.jooq.org/doc/latest/manual/code-generation/codegen-maven/) or Flyways Maven plugins.

## Ezkv's advantages:

* **Zero opinions** - it does not assume you want to load `app.properties` first. You define what resources and order.
* Zero dependencies
* Zero reflection
* Zero auto loading - you pick that
* Zero logging (unless you want it)
*  a `module-info.java`, jspecify annotations, and  jdk 21 ready.
* **Framework agnostic!**
* Fast initialization
* Simple interpolation system that can be disabled for certain resources
* Certain resources can be treated as sensitive
* Extendable key value format loading
* Chaining of overriding key values
* Can simulate other configuration frameworks loading easily with k/v configuration.
* Allow users to choose where configuration comes from without recompiling.

The last point is what really separates out Ezkv from other systems. 
For example if you want to use environment variables with some special prefix without recompiling the application
you can. Or perhaps you want to use a special properties file in your home directory.
While there are applications and such that allow naive file *"includes"* (for example NGINX) but usually it is only files.

## Architecture

Ezkv's two major concepts are:

1. KeyValues - a stream of key values
1. Resources - a URI with associated key value meta data.

Resources are used to load key values and key values can be used to specify and find more resources 
(to load more key values). Ezkv is recursive.

For the rest of the explanation of architecture we will go bottom up.

### KeyValue

A `KeyValue` object in Ezkv unlike a `Map.Entry<String,String>` has more information than just the simple tuple of `key` and `value`. 

`KeyValue` are:

* Immutable
* Have interpolated value as well as the original pre-interpolated value
* Have source information
* Whether or not it should be used for interpolation
* Whether or not it should be printed out ever (e.g. a password or other sensitive information)

Ezkv provides ergonomics working with streams of key values to filter, and collect key values
as well as parse and format.

**Notice that Ezkv is like a list of key values and thus:**

* Order can be important
* There can be duplicate "keys" (that may or may not override in the final result)

Finally a `KeyValue` can be a special key that can reference another resource to load.

These keys are usually prefixed with `_` to avoid collision and maximum compatibility.
The most important one is `_load_name` where name is the name you like to give the resource and the value is a `URI`.
This mini DSL syntax in the future will be configurable so that you can pick different key name patterns.

### Interpolation

Ezkv can do Bash like interpolation on a stream of `KeyValues` (e.g. `${somevar:-${othervar}}`). It does this by using
the key values themselves and `Variables`. `Variables` are simply `Function<String,String>`.
This allows you to interpolate on key values with things you do not want in the final
result (`KeyValues`). For example a common practice is to use `System.getProperties()` as variables
but often you do not want all of the system properties to end up in the `KeyValues`.

Interpolation can be disabled with the resource flag `no_interpolation`.

Furthermore you can load up a resource as variables instead of `KeyValues`
with the resource flag `no_add`.

```properties
# first loaded properties
_load_system=system:///
_flags_system=no_add
_load_app=classpath:/${app.name:-app}.properties
```

If `app.name` was defined in system properties it would use it in the `_load_app` URI otherwise `app.properties` 
will be used.

Often interpolation will create a new stream of KeyValues where the value
part of the key is replaced with the interpolated results however the original value
is always retained.

### KeyValuesResource 

A `KeyValuesResource` has a `URI` and symbolic name (used to find configuration). 
It is backed by a key value with additional meta data on how to load that resource. 
URIs are designed to point at resources and the additional meta data 
in a `KeyValuesResource` surprise surprise is more `KeyValues`. 

The additional meta data is used to know how to load the key values 
and what meta data should be associated with each key value.

Some examples are:

* The key values from the resource are sensitive and should not be easily printed out
* The key values should not be interpolated because the data is raw
* The loaded key values should or should not load other key values
* The key values need their names transformed or some key values ignored

This is all configurable again through key values (and URIs) particularly
the `_flags_name` key.

#### Resource Key Value Configuration

Resource loading configuration can be done with special key values.
It can be either specified in the URI of the resource or as key values in
the resource where the `_load_[name]` is specified.

The underscore is a called the prefix. 
That will be configurable at some point as well as the separator
which is also an underscore.

The key names without the prefix and separators are:

* `load` - resource to load required
* `mediaType` or `mime` - media type of resource not required
* `flags` or `flag` - flags for loading
* `param` or `parm` - parameters for custom plugins
* `filter` or `filt` - filters

The next sections will cover the default key syntax within a resource
or within a URI. 

##### Resource configuration in resource

The default key value pattern to specify resources is:

```properties
_load_[name]=URI
_mediaType_[name]=Content Type or file extension to resolve format of resource for parsing
_flags_[name]=CSV of flag names
_param_[name]_[key]=String
_filter_[name]_[filter]=String expression for filter
```

The `[name]` part should be replaced with a name of ones choosing where only case sensitive alphanumeric characters are allowed.
It becomes the symbolic name of the resource. Don't worry those special keys will be filtered out. 

The `_load_[name]` is the most important key as it dictates the name of the resource and the URI of the resource.


**TIP:** the key aliases are all 4 characters which is useful for visual alignment.


```properties
_load_[name]=URI
_mime_[name]=Content Type or file extension to resolve format of resource for parsing
_flag_[name]=CSV of flag names
_parm_[name]_[key]=String
_filt_[name]_[filter]=String expression for filter
```

##### Resource configuration in URI

The URI of the `_load_[name]` can also contribute to resource keys with the following format

```properties
_load_custom=file://./something?_mediaType=properties&_flags=optional&_filter_sed=s/myapp_//&_param_custom=something
```

**Notice how the resource name does not need to be specified with URI parameters as that is deduced.**

#### Resource Flags

Resource flags can be set with `_flags_[name]` in the resource or on the URI with `_flags`
which can be a repeatable parameter and is combined.

This is currently a subset of the flags:

* `no_require` / `optional` - Resources are usually required to exist otherwise failure. This flag makes them not required.
* `sensitive` - The key values loaded from the resource will be marked as sensitive and thus will not be outputted on `toString` etc.
* `no_add` - The key values will not be added to the final result but will be used as `variables`.
* `no_load_children` - The resource is not allowed to chain additional resources (not allowed to use `_load_`).

#### Resource Media Type

Ezkv will try to automatically determine the media type of a resource based on the URI file extension but
in some cases that is not enough. The key `_mediaType_[name]` allows you to provide that explicitly.

This is especially useful on URIs that can load a key that has key values in it.
A real world example of that is in [Spring Boot](https://docs.spring.io/spring-boot/reference/features/external-config.html)
`SPRING_APPLICATION_JSON` environment variable.

An example of emulating that behavior is:

```properties
_load_springJson=env:///SPRING_APPLICATION_JSON?_mediaType=json
```

(A caveat with the above is example is that JSON support will be a separate module and is not provided OOB).

#### Resource Filters

Filters can be applied to a resource that will alter the key values after loaded but before being
added to the final results. The ordering of filters does matter so it is generally
recommend to use the URI notation as order is guaranteed.

The current filters provided OOB are:

* sed - just like the unix sed utility but only supporting the `s` and `d` commands
* grep - filter keys matching regular expression

Here is an example of using both:

```properties
_load_env=env:///?_filter_grep=^MY_APP_&_filter_sed=s/^MY_APP/myapp./
```

The above will only load environment variables prefixed with `MY_APP_` and will replace
`MY_APP_` with `myapp.`

The original key name is always preserved for key word tracking so regardless
of how you rename people can still find where the key value originally came from.


### KeyValuesLoader

A KeyValues loader usually takes a  `KeyValuesResource` and turns it into `KeyValues`.

It is an extension point that in simple terms takes a URI and loads `KeyValues` usually
based on the schema of the URI. For example `classpath` will use the JDK 
classloader mechanism and `file` will use `java.io`/`java.nio` file loading.

This part of the library is extendable and custom loaders can be manually wired or the service loader can be used.

Out of the box Ezkv supports by schema:

* `classpath` - classpath resource
* `file` - file resource 
* `system` - System properties
* `env` - Environment variables
* `cmd` - Command line argument pairs separated by `=`.
* `stdin` - Allows unix piping of key values often useful for passwords
* `profile.[schema]` - Will load multiple resources based on a CSV of profiles where the profile name replaces part of the URI.

Other URI schemas will be added soon usually as separate modules like dotenv, HOCON, Terraform/Tofu `tfvars.json` format etc.

**Note that if no schema is provided Ezkv will assume it is a file path which is a URI.**

We won't cover `file` and `classpath` as they are pretty self explanatory.

#### URI schemas: `env`, `system` and `cmd` URI schemas 

`env:///`, `system:///` and `cmd:///` resources have a few features that are different than file or classpath.

Each one of the URI schemas above can take a path which will be used to fetch a key value where the value is used as
the source of the key values. Otherwise all the key values of the resource will be loaded.

For example let us assume we have an env variable whose value is JSON we can load it like:

```properties
_load_springJson=env:///SPRING_APPLICATION_JSON?_mediaType=json
```

#### URI schemas: `stdin`

`stdin:///` without a path will assume the entire contents of the stdin is in `java.util.Properties` key values format.
Thus it is a good idea to specify `_mediaType=` with stdin if that is not the case.

`stdin:///` can also bind the contents of the input parsed as UTF-8 string to the key provided in the path of the URI.

```properties
_load_stdin=stdin:///db.password?_flag=sensitive,optional
```

As you can see the above is particularly useful for passwords similar to [how docker takes passwords from stdin](https://docs.docker.com/reference/cli/docker/login/#password-stdin).


#### URI schema: `profile.`

(TODO we will probably rename profile to profiles soon)

Probably the more confusing KeyValuesLoader is the `profile` which deserves an example:

```properties
PROFILES=profile1,profile2 # this could come from env variable

_load_profiles=profile.classpath:/app-__PROFILE__.properties
_param_profiles_profile=${PROFILES}
_flags_profiles=no_require
```

Which will try to load:

* `classpath:/app-profile1.properties`
* `classpath:/app-profile2.properties`

But not fail if those resources are not found.

The above is not special internal encapsulated logic. The profiles loader just
generates key values:

```properties
_load_profiles1=classpath:/app-profile1.properties
_flags_profiles1=no_require
_load_profiles2=classpath:/app-profile2.properties
_flags_profiles2=no_require
```

So one can make something similar if they like.

### KeyValuesMedia

Some `KeyValuesLoader` will know how to parse the `URI` directly to key values
BUT many will will want to use a parser. 

Ezkv provides a framework to parse and format key values from/to byte streams, 
or strings based on ["media type" aka "Content Type" aka MIME](https://en.wikipedia.org/wiki/Media_type) 
or file extension.

This part of the library is extendable and custom media types can be manually wired or the service loader can be used.

Out of the box Ezkv supports:

 * `java.util.Properties` format.
 * URL Query percent encoding format.

Other formats will be added soon usually as separate modules like dotenv, HOCON, Terraform/Tofu `tfvars.json` format etc.

### KeyValuesEnvironment

`KeyValuesEnvironment`

Ezkv provides a facade to access system properties, environment variables, class resource loaders, stdin,
command line arguments and logging. This can be useful for unit testing but the most useful is the logger.

The logger in Ezkv by default does nothing but users may want to plug their own implementations to keep
track of resource loading.

### KeyValuesSystem

`KeyValuesSystem`

This is the entrypoint into Ezkv and used to load the initial part of the chain of resources.
The bootstrapping part of your application will call it first and will often convert or pass
the loaded key values to something else.

## History

This library is a rewrite of an organic part of my companies code base that
has been evolving for over 14 years.

Many libraries and frameworks have come and gone with differing opinions on configuration. 
While our backing frameworks have changed over the years our configuration style, format
and behavior because of this library has largely not thanks to the flexibility.

*Opinionated maybe vogue but **not** opinionated goes the distance.*

## Other work

* [avaje-config](https://avaje.io/config/) 

Ezkv hopes to bring many of its concepts and design to `avaje-config`.
