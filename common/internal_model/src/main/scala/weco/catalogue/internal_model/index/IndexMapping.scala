package weco.catalogue.internal_model.index

object IndexMapping {

  /** Return a JSON string suitable for posting to Elasticsearch as a Mapping object.
    *
    * Given a JSON string representing the properties for an index mapping, populate a "mappings"
    * object accordingly.
    *
    * This essentially replaces the practice of calling methods on an elastic4s MappingDefinition to
    * update a mapping with values for dynamic and _meta etc. (which do not work with a definition
    * initialised with rawSource), allowing us to configure the properties in a mapping with plain
    * JSON.
    */
  def apply(propertiesJson: String): String = {
    // Here we set dynamic strict to be sure the object vaguely looks like an
    // image and contains the core fields, adding DynamicMapping.False in places
    // where we do not need to map every field and can save CPU.
    s"""{
       |"properties": $propertiesJson,
       |"dynamic": "strict"
       |}""".stripMargin
  }

}
