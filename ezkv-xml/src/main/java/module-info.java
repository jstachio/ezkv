/**
 * Provides an XML capable key values media parser
 * that depends on the JDK <code>java.xml</code> module
 * (<a href="https://search.maven.org/artifact/io.jstach.ezkv/ezkv-xml/_VERSION_/jar">
 * io.jstach.ezkv:ezkv-xml:_VERSION_
 * </a>). 
 * 
 * @provides io.jstach.ezkv.kvs.KeyValuesServiceProvider
 * @see io.jstach.ezkv.xml.XMLKeyValuesMedia
 */
module io.jstach.ezkv.xml {
	exports io.jstach.ezkv.xml;
	
	requires transitive io.jstach.ezkv.kvs;
	requires org.jspecify;
	requires java.xml;
	
	provides io.jstach.ezkv.kvs.KeyValuesServiceProvider with io.jstach.ezkv.xml.XMLKeyValuesMedia;
}