package uk.ac.wellcome.platform.api.models

import io.circe.generic.extras.semiauto._
import io.circe.Encoder
import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.display.models._
import uk.ac.wellcome.display.json.DisplayJsonUtil._
import uk.ac.wellcome.models.work.internal.IdState.Minted
import uk.ac.wellcome.models.work.internal._

@Schema(
  name = "Aggregations",
  description = "A map containing the requested aggregations."
)
case class DisplayWorkAggregations(
  @Schema(
    description = "Format aggregation on a set of results."
  ) workType: Option[DisplayAggregation[DisplayFormat]],
  @Schema(
    description = "Date aggregation on a set of results."
  ) @JsonKey("production.dates") productionDates: Option[
    DisplayAggregation[DisplayPeriod]],
  @Schema(
    description = "Genre aggregation on a set of results."
  ) genres: Option[DisplayAggregation[DisplayGenre]],
  @Schema(
    description = "Subject aggregation on a set of results."
  ) subjects: Option[DisplayAggregation[DisplaySubject]],
  @Schema(
    description = "Contributor aggregation on a set of results."
  ) contributors: Option[DisplayAggregation[DisplayContributor]],
  @Schema(
    description = "Language aggregation on a set of results."
  ) languages: Option[DisplayAggregation[DisplayLanguage]],
  @Schema(
    description = "License aggregation on a set of results."
  ) license: Option[DisplayAggregation[DisplayLicense]],
  @Schema(
    description = "Location type aggregation on a set of results."
  ) locationType: Option[DisplayAggregation[DisplayLocationTypeAggregation]],
  @Schema(
    description = "Availabilities aggregation on a set of results."
  ) availabilities: Option[DisplayAggregation[DisplayAvailability]],
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Aggregations")

object DisplayWorkAggregations {

  implicit def encoder: Encoder[DisplayWorkAggregations] =
    deriveConfiguredEncoder

  def apply(aggs: WorkAggregations): DisplayWorkAggregations =
    DisplayWorkAggregations(
      workType = displayAggregation(aggs.format, DisplayFormat.apply),
      productionDates =
        displayAggregation(aggs.productionDates, DisplayPeriod.apply),
      genres = displayAggregation[Genre[Minted], DisplayGenre](
        aggs.genres,
        DisplayGenre(_, includesIdentifiers = false)),
      languages = displayAggregation(aggs.languages, DisplayLanguage.apply),
      subjects = displayAggregation[Subject[Minted], DisplaySubject](
        aggs.subjects,
        subject => DisplaySubject(subject, includesIdentifiers = false)
      ),
      contributors =
        displayAggregation[Contributor[Minted], DisplayContributor](
          aggs.contributors,
          contributor =>
            DisplayContributor(contributor, includesIdentifiers = false)
        ),
      license = displayAggregation(aggs.license, DisplayLicense.apply),
      locationType = displayAggregation(
        aggs.locationType,
        DisplayLocationTypeAggregation.apply),
      availabilities =
        displayAggregation(aggs.availabilities, DisplayAvailability.apply)
    )

  private def displayAggregation[T, D](
    maybeAgg: Option[Aggregation[T]],
    display: T => D): Option[DisplayAggregation[D]] =
    maybeAgg.map {
      DisplayAggregation(_, display)
    }
}
