package uk.ac.wellcome.platform.api.models

import scala.collection.JavaConverters._
import io.swagger.models.parameters.QueryParameter
import io.swagger.models.properties.StringProperty
import uk.ac.wellcome.display.models.V2WorksIncludes

object Swagger {
  def enumParam(name: String,
                description: String,
                items: List[String]): QueryParameter =
    new QueryParameter()
      .name(name)
      .description(description)
      .items(new StringProperty()
        ._enum(items.asJava))
      .`type`("array")
      .collectionFormat("csv")
      .required(false)

  def csvParam(name: String,
               description: String,
               examples: List[String]): QueryParameter =
    new QueryParameter()
      .name(name)
      .description(description)
      .items(new StringProperty())
      .`type`("array")
      .collectionFormat("csv")
      .required(false)
      .example(examples.mkString(","))

  val includeParam: QueryParameter = enumParam(
    "include",
    "A comma-separated list of extra fields to include",
    V2WorksIncludes.recognisedIncludes)

  val itemsLocationsLocationType: QueryParameter = csvParam(
    "items.locations.locationType",
    "A comma-separated list of locationTypes to filter by. Values are concatenated as `OR`s",
    List("iiif-image", "iiif-manifest")
  )
}
