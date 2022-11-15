package weco.catalogue.internal_model.index

import weco.json.JsonUtil.toJson


object IndexMapping {
  /**
   * Return a JSON string suitable for posting to Elasticsearch as a Mapping object.
   *
   * Given a JSON string representing the properties for an index mapping,
   * populate a "mappings" object accordingly.
   *
   * This essentially replaces the practice of calling methods on an elastic4s MappingDefinition
   * to update a mapping with values for dynamic and _meta etc.
   * (which do not work with a definition initialised with rawSource),
   * allowing us to configure the properties in a mapping with plain JSON.
   *
   */
  def apply(propertiesJson: String, buildVersion: String): String = {
    val version = buildVersion.split("\\.").toList
    val meta = toJson(Map(s"model.versions.${version.head}" -> version.tail.head)).get
    s"""{
       |"properties": $propertiesJson,
       |"_meta": $meta,
       |"dynamic": "strict"
       |}""".stripMargin
  }

}
