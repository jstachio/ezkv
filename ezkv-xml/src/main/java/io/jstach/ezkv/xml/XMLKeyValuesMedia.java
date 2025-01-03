package io.jstach.ezkv.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jspecify.annotations.Nullable;

import io.jstach.ezkv.kvs.KeyValuesMedia;
import io.jstach.ezkv.kvs.KeyValuesMedia.Parser;
import io.jstach.ezkv.kvs.KeyValuesResource;
import io.jstach.ezkv.kvs.Variables;

//@formatter:off
/**
 * Parses XML to KeyValues.
 * <p>
 * The constants in this class are important and describe parameters that can be
 * set on the {@link KeyValuesResource}.
 * </p>
 * <p>
 * XML is not flat and is more of a tree where as EZKV KeyValues are and only
 * support string values. <strong>Thus the parser flattens the XML based on some
 * heuristics.</strong>
 * </p>
 *
 * Here is an example of the default flattening of the following XML:
 *
 * {@snippet lang = xml :
 * <a>
 * 	<b>1</b>
 * 	<b>2.0
 * 		<c>3</c>
 * 	</b>
 * 	<d>value</d>
 *  <e f="attr" />
 * </a>
 * }
 *
 * Here is the corresponding key values (notice duplicates):
 *
 * {@snippet lang = properties :
 * a.b=1
 * a.b=2.0
 * a.b.c=3
 * a.d=value
 * a.e.f=attr
 * }
 *
 * <p>
 * Because EZKV supports duplicates and order matters the data is mostly not
 * lost but this might be confusing or ambiguous. Another option is to generate
 * XPATH based keys with indices.
 * </p>
 *
 * Assume we load a resource with the previous XML like:
 *
 * {@snippet lang = properties :
 *
 * _load_a=classpath:/a.xml5?_param_xml_mode=xpath
 *
 * }
 *
 * We will get flattened key values that look like:
 *
 * {@snippet lang = properties :
 *  /a/b[1]=1
 *  /a/b[2]=2.0
 *  /a/b[2]/c=3
 *  /a/d=value
 *  /a/e/@f
 * }
 *
 * Because both formats may not be what you want you might want to use a
 * {@linkplain KeyValuesFilter filter} to clean up the results.
 * 
 * <p>
 * Both modes of {@value #MODE_PARAM} ({@link #MODE_VALUE_XPATH} and {@link #MODE_VALUE_PROPERTIES}) 
 * can support array indices or not.
 * </p>
 *
 * <p>
 * This module does have a service loader registration. If you do not wish to
 * use the Service Loader you can add an instance of this class to
 * {@link io.jstach.ezkv.kvs.KeyValuesSystem.Builder}.
 * </p>
 *
 * @apiNote This is not included with the core ezkv module because XML requires <code>java.xml</code>.
 * @see #ARRAY_KEY_PARAM
 * @see #SEPARATOR_PARAM
 * @see #MODE_PARAM
 *
 */
// @formatter:on

public class XMLKeyValuesMedia implements KeyValuesMedia {

	/**
	 * The media type is {@value #MEDIA_TYPE} and not <code>application/xml</code> however
	 * this media will work for all sorts of XML.
	 */
	public static final String MEDIA_TYPE = "text/xml";

	/**
	 * The file ext is {@value #FILE_EXT} but will work with XML that does not have that
	 * file ext.
	 */
	public static final String FILE_EXT = "xml";

	// @formatter:off
	/**
	 * This parameter controls the array key naming scheme and is set with
	 * {@link KeyValuesResource#parameters()}. The two values are
	 *
	 * {@value #ARRAY_KEY_VALUE_VALUE} (default) and
	 * {@value #ARRAY_KEY_VALUE_ARRAY}.
	 *
	 * An example is:
	 * {@snippet lang = properties :
	 *
	 * _load_a=classpath:/a.xml?_param_xml_arraykey=array
	 *
	 * }
	 */
	// @formatter:on
	public static final String ARRAY_KEY_PARAM = "xml_arraykey";

	/**
	 * Will output array keys using array notation like <code>a[0]</code> instead of the
	 * default which is duplicating the keys.
	 */
	public static final String ARRAY_KEY_VALUE_ARRAY = "array";

	// @formatter:off
	/**
	 * Will duplicate keys for array entries. For example
	 * <code>{ "a" : [ 1, 2 ] }</code> will be
	 * {@snippet lang = properties :
	 * a=1
	 * a=2
	 * }
	 */
	// @formatter:on
	public static final String ARRAY_KEY_VALUE_VALUE = "duplicate";

	/**
	 * Configures the separator by {@link KeyValuesResource#parameters()} with the key
	 * {@value #SEPARATOR_PARAM} and is by default {@value #DEFAULT_SEPARATOR}.
	 */
	public static final String SEPARATOR_PARAM = "xml_separator";

	/**
	 * The default XML path separator.
	 */
	public static final String DEFAULT_SEPARATOR = ".";

	/**
	 * Either {@value #MODE_VALUE_PROPERTIES} or {@value #MODE_VALUE_XPATH} the default is
	 * {@value #MODE_VALUE_PROPERTIES}.
	 */
	public static final String MODE_PARAM = "xml_mode";

	/**
	 * This will use XPATH notation which for the array if there are multiple elements
	 * with the same name and parent will use array notation with <strong>1</strong> index
	 * notation.
	 */
	public static final String MODE_VALUE_XPATH = "xpath";

	/**
	 * This will use properties notation where "." is the separator and duplicates do not
	 * have any array notation.
	 */
	public static final String MODE_VALUE_PROPERTIES = "properties";

	/**
	 * Constructor called by service loader.
	 */
	public XMLKeyValuesMedia() {
	}

	@Override
	public String getMediaType() {
		return MEDIA_TYPE;
	}

	@Override
	public @Nullable String getFileExt() {
		return FILE_EXT;
	}

	@Override
	public Parser parser() {
		return parser(Variables.empty());
	}

	enum Parameter implements Variables.Parameter {

		PREFIX("xml_prefix", "", "/"), //
		SEPARATOR(SEPARATOR_PARAM, DEFAULT_SEPARATOR, "/"), //
		MODE(MODE_PARAM, MODE_VALUE_PROPERTIES, MODE_VALUE_XPATH), //
		ATTRIBUTE("xml_attributePrefix", "", "@"), //
		ARRAY(ARRAY_KEY_PARAM, ARRAY_KEY_VALUE_VALUE, ARRAY_KEY_VALUE_ARRAY), //
		INDEX_START("xml_indexStart", "0", "1");

		final String key;

		final String propertiesValue;

		final String xpathValue;

		private Parameter(String key, String propertiesValue, String xpathValue) {
			this.key = key;
			this.propertiesValue = propertiesValue;
			this.xpathValue = xpathValue;
		}

		static void fill(XmlMode mode, Variables.Builder builder) {

			switch (mode) {
				case PROPERTIES -> {
					fillProperties(builder);
				}
				case XPATH -> {
					fillXPath(builder);
				}
				default -> {
					throw new IllegalArgumentException("mode is incorrect and should be either: '%s' or '%s'"
						.formatted(MODE_VALUE_PROPERTIES, MODE_VALUE_XPATH));
				}
			}

		}

		static void fillXPath(Variables.Builder builder) {
			for (Parameter p : Parameter.values()) {
				builder.add(p.key, p.xpathValue);
			}
		}

		static void fillProperties(Variables.Builder builder) {
			for (Parameter p : Parameter.values()) {
				builder.add(p.key, p.propertiesValue);
			}
		}

		@Override
		public List<String> keys() {
			return List.of(key);
		}

	}

	private enum XmlMode {

		PROPERTIES, XPATH;

	}

	@Override
	public Parser parser(Variables parameters) {
		var defaults = Variables.builder().add(parameters);
		XmlMode mode = Parameter.MODE.requireElse(defaults.build(), m -> switch (m) {
			case MODE_VALUE_PROPERTIES -> XmlMode.PROPERTIES;
			case MODE_VALUE_XPATH -> XmlMode.XPATH;
			default -> {
				throw new IllegalArgumentException("Mode is incorrect and should be either: '%s' or '%s'"
					.formatted(MODE_VALUE_PROPERTIES, MODE_VALUE_XPATH));
			}
		}, XmlMode.PROPERTIES);
		Parameter.fill(mode, defaults);
		parameters = Variables.builder().add(parameters).add(defaults.build()).build();
		String prefix = Parameter.PREFIX.require(parameters);
		String separator = Parameter.SEPARATOR.require(parameters);
		String attributePrefix = Parameter.ATTRIBUTE.require(parameters);
		boolean array = Parameter.ARRAY.require(parameters, v -> switch (v) {
			case ARRAY_KEY_VALUE_VALUE -> false;
			case ARRAY_KEY_VALUE_ARRAY -> true;
			case String s -> {
				throw new IllegalArgumentException(
						Parameter.ARRAY.key + " is incorrect and should be either: '%s' or '%s', value = '%s'"
							.formatted(ARRAY_KEY_VALUE_VALUE, ARRAY_KEY_VALUE_ARRAY, s));
			}
		});
		int indexStart = Parameter.INDEX_START.require(parameters, Integer::parseInt);
		return new XMLFlattener(prefix, separator, attributePrefix, array, indexStart);
	}

}

class XMLFlattener implements Parser {

	private final String prefix;

	private final String separator;

	private final String attributePrefix;

	private final boolean array;

	private final int indexStart;

	static final XMLFlattener XPATH_FLATTENER = new XMLFlattener("", XMLKeyValuesMedia.DEFAULT_SEPARATOR, "", false, 0);

	public XMLFlattener(String prefix, String separator, String attributePrefix, boolean array, int indexStart) {
		super();
		this.prefix = prefix;
		this.separator = separator;
		this.attributePrefix = attributePrefix;
		this.array = array;
		this.indexStart = indexStart;
	}

	@Override
	public void parse(InputStream input, BiConsumer<String, String> consumer) throws IOException {
		try {
			flattenXML(new InputStreamReader(input), consumer);
		}
		catch (XMLStreamException e) {
			throw new IOException(e);
		}
	}

	@Override
	public void parse(String input, BiConsumer<String, String> consumer) {
		try {
			flattenXML(new StringReader(input), consumer);
		}
		catch (XMLStreamException e) {
			throw new RuntimeException(e);
		}

	}

	@Override
	public String toString() {
		return "XMLFlattener [prefix=" + prefix + ", separator=" + separator + ", attributePrefix=" + attributePrefix
				+ ", array=" + array + ", indexStart=" + indexStart + "]";
	}

	void flattenXML(Reader reader, BiConsumer<String, String> consumer) throws XMLStreamException {
		var ents = entries(reader);
		for (var e : ents) {
			String key = keyName(e);
			String value = e.getValue();
			if (value != null) {
				consumer.accept(key, value);
			}
		}
	}

	protected String attributeName(Attr attribute) {
		return this.attributePrefix + attribute.name;
	}

	protected String elementName(Elm segment) {
		if (segment.index == 0 || !array) {
			return segment.name;
		}
		int index = (segment.index - 1) + indexStart;
		return segment.name + "[" + index + "]";
	}

	protected String buildXPath(Elm element) {
		// Build the XPath string by joining the stack elements with '/'
		String path = element.path().map(this::elementName).collect(Collectors.joining(this.separator));
		return this.prefix + path;
	}

	protected String buildXPath(Attr attr) {
		String path = buildXPath(attr.parent);
		return path + this.separator + attributeName(attr);
	}

	protected String keyName(Ent ent) {
		return switch (ent) {
			case Elm e -> buildXPath(e);
			case Attr a -> buildXPath(a);
		};
	}

	sealed abstract class Ent {

		final String name;

		public Ent(String name) {
			super();
			this.name = name;
		}

		abstract @Nullable String getValue();

	}

	final class Elm extends Ent {

		final @Nullable Elm parent;

		private int index = 0;

		private @Nullable String value;

		Map<String, List<Elm>> children = new HashMap<>();

		public Elm(String name, @Nullable Elm parent) {
			super(name);
			this.parent = parent;
		}

		Stream<Elm> path() {
			ArrayList<Elm> elms = new ArrayList<>();
			var parent = this;
			while (parent != null) {
				elms.add(parent);
				parent = parent.parent;
			}
			Collections.reverse(elms);
			return elms.stream();
		}

		public void addChild(Elm child) {
			children.computeIfAbsent(child.name, (k) -> new ArrayList<Elm>()).add(child);
		}

		public void addValue(String text) {
			String v = this.value;
			if (v == null) {
				this.value = text;
			}
			else {
				this.value = v + text;
			}
		}

		public void adjust() {
			for (var e : children.entrySet()) {
				adjust(e.getValue());
			}
		}

		void adjust(List<Elm> children) {
			boolean array = children.size() > 1;
			if (!array) {
				return;
			}
			int index = 1;
			for (var c : children) {
				c.index = index++;
			}
		}

		int getIndex() {
			return index;
		}

		@Override
		String getValue() {
			return value;
		}

		@Override
		public String toString() {
			return "Elm [name=" + name + ", index=" + index + ", value=" + value + ", children=" + children + "]";
		}

	}

	final class Attr extends Ent {

		final String value;

		final Elm parent;

		public Attr(String name, String value, Elm parent) {
			super(name);
			this.value = value;
			this.parent = parent;
		}

		@Override
		@Nullable
		String getValue() {
			return value;
		}

	}

	List<Ent> entries(Reader reader) throws XMLStreamException {
		XMLInputFactory factory = XMLInputFactory.newInstance();
		XMLStreamReader xr = factory.createXMLStreamReader(reader);

		// Stack to maintain the current path as we traverse the XML
		List<Ent> entries = new ArrayList<>();

		Elm current = null;
		while (xr.hasNext()) {
			int event = xr.next();
			switch (event) {
				case XMLStreamConstants.START_ELEMENT -> {
					Elm elm = new Elm(xr.getLocalName(), current);
					if (current != null) {
						current.addChild(elm);
					}
					entries.add(elm);

					// Process attributes as XPath-style key-value pairs
					for (int i = 0; i < xr.getAttributeCount(); i++) {
						String name = xr.getAttributeLocalName(i);
						String value = xr.getAttributeValue(i);
						Attr attr = new Attr(name, value, elm);
						entries.add(attr);
					}
					current = elm;
				}
				case XMLStreamConstants.CHARACTERS -> {
					if (current == null) {
						throw new IllegalStateException("bug");
					}
					if (!xr.isWhiteSpace()) {
						// String textXPath = buildXPath(current);
						String value = xr.getText();
						current.addValue(value);
					}
				}
				case XMLStreamConstants.END_ELEMENT -> {
					if (current != null) {
						current.adjust();
						current = current.parent;
					}
				}
			}
		}
		return entries;
	}

}
