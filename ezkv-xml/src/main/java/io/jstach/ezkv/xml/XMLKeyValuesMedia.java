package io.jstach.ezkv.xml;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.function.BiConsumer;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.jspecify.annotations.Nullable;

import io.jstach.ezkv.kvs.KeyValuesMedia;
import io.jstach.ezkv.kvs.KeyValuesMedia.Parser;
import io.jstach.ezkv.kvs.KeyValuesResource;
import io.jstach.ezkv.kvs.KeyValuesServiceProvider.KeyValuesFilter;

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
 * Here is an example of the default flattening:
 * 
 * {@snippet lang = xml :
 * <a>
 * 	<b>1</b>
 * 	<b>2.0
 * 		<c>3</c>
 * 	</b>
 * 	<d>value</d>
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
 * _load_a=classpath:/a.xml5?_param_xml_arraykey=array
 *
 * }
 * 
 * We will get flattened key values that look like:
 * 
 * {@snippet lang = properties :
 * a.b[0]=1
 * a.b[1]=2.0
 * a.b[1].c=3
 * a.d=value
 * }
 * 
 * Because both formats may not be what you want you might want to use a
 * {@linkplain KeyValuesFilter filter} to clean up the results.
 *
 * <p>
 * This module does have a service loader registration. If you do not wish to
 * use the Service Loader you can add an instance of this class to
 * {@link io.jstach.ezkv.kvs.KeyValuesSystem.Builder}.
 * </p>
 * 
 * @apiNote This is not included with the core ezkv module because JSON5 is
 *          still evolving and likely to keep changing.
 * @see #ARRAY_KEY_PARAM
 * @see #SEPARATOR_PARAM
 *
 */
// @formatter:on

public class XMLKeyValuesMedia implements KeyValuesMedia {
	/**
	 * The media type is {@value #MEDIA_TYPE} and not
	 * <code>application/xml</code> however this media will work for all sorts
	 * of XML.
	 */
	public static final String MEDIA_TYPE = "text/xml";

	/**
	 * The file ext is {@value #FILE_EXT} but will work with XML that does not
	 * have that file ext.
	 */
	public static final String FILE_EXT = "xml";

	// @formatter:off
	/**
	 * This parameter controls the array key naming scheme and is set with
	 * {@link KeyValuesResource#parameters()}. The two values are
	 *
	 * {@value #ARRAY_KEY_DUPLICATE_VALUE} (default) and
	 * {@value #ARRAY_KEY_ARRAY_VALUE}.
	 *
	 * An example is:
	 * {@snippet lang = properties :
	 *
	 * _load_a=classpath:/a.json5?_param_json5_arraykey=array
	 *
	 * }
	 */
	// @formatter:on
	public static final String ARRAY_KEY_PARAM = "xml_arraykey";

	/**
	 * Will output array keys using array notation like <code>a[0]</code>
	 * instead of the default which is duplicating the keys.
	 */
	public static final String ARRAY_KEY_ARRAY_VALUE = "array";

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
	public static final String ARRAY_KEY_DUPLICATE_VALUE = "duplicate";

	/**
	 * Configures the separator by {@link KeyValuesResource#parameters()} with
	 * the key {@value #SEPARATOR_PARAM} and is by default
	 * {@value #DEFAULT_SEPARATOR}.
	 */
	public static final String SEPARATOR_PARAM = "xml_separator";

	/**
	 * The default JSON path separator.
	 */
	public static final String DEFAULT_SEPARATOR = ".";

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
		return new XMLFlattener();
	}
}

class XMLFlattener implements Parser {
	
	@Override
	public void parse(
			InputStream input,
			BiConsumer<String, String> consumer)
			throws IOException {
		try {
			flattenXML(new InputStreamReader(input), consumer);
		} catch (XMLStreamException e) {
			throw new IOException(e);
		}
	}
	
	@Override
	public void parse(
			String input,
			BiConsumer<String, String> consumer) {
		try {
			flattenXML(new StringReader(input), consumer);
		} catch (XMLStreamException e) {
			throw new RuntimeException(e);
		}

	}

	public static void flattenXML(
			Reader _reader,
			BiConsumer<String, String> consumer) throws XMLStreamException {
		XMLInputFactory factory = XMLInputFactory.newInstance();
		XMLStreamReader reader = factory.createXMLStreamReader(_reader);

		StringBuilder path = new StringBuilder();

		while (reader.hasNext()) {
			int event = reader.next();

			switch (event) {
			case XMLStreamConstants.START_ELEMENT:
				if (path.length() > 0) {
					path.append('.');
				}
				path.append(reader.getLocalName());

				// Process attributes
				for (int i = 0; i < reader.getAttributeCount(); i++) {
					String attrKey = path + "." + reader.getAttributeLocalName(i);
					String attrValue = reader.getAttributeValue(i);
					consumer.accept(attrKey, attrValue);
				}
				break;

			case XMLStreamConstants.CHARACTERS:
				if (!reader.isWhiteSpace()) {
					String value = reader.getText();
					consumer.accept(path.toString(), value);
				}
				break;

			case XMLStreamConstants.END_ELEMENT:
				int lastDotIndex = path.lastIndexOf(".");
				if (lastDotIndex != -1) {
					path.setLength(lastDotIndex); // Remove the last element
													// from the path
				} else {
					path.setLength(0); // Clear the path for root-level elements
				}
				break;
			}
		}
	}

}
