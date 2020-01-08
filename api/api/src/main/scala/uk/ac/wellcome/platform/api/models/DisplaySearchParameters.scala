package uk.ac.wellcome.platform.api.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.platform.api.services.WorksSearchOptions
@Schema(
  name = "SearchParameters",
  description = "The parameters used to fetch a set of results.")
case class DisplaySearchParameters(
  queryType: Option[String],
  @JsonKey("type") @Schema(name = "type") ontologyType: String =
    "SearchParameters")

object DisplaySearchParameters {
  def apply(searchOptions: WorksSearchOptions): DisplaySearchParameters = {

    val queryType =
      searchOptions.searchQuery.map(_.queryType.name)

    DisplaySearchParameters(queryType = queryType)
  }
}
