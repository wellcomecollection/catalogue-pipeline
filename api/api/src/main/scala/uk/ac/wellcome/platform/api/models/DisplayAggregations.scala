package uk.ac.wellcome.platform.api.models

import io.circe.generic.extras.semiauto._
import io.circe.Encoder
import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema
import uk.ac.wellcome.display.models._
import uk.ac.wellcome.models.work.internal._

@Schema(
  name = "Aggregations",
  description = "A map containing the requested aggregations."
)
case class DisplayAggregations(
  @Schema(
    description = "WorkType aggregation on a set of results."
  ) workType: Option[DisplayAggregation[DisplayWorkType]],
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
    description = "Language aggregation on a set of results."
  ) language: Option[DisplayAggregation[DisplayLanguage]],
  @Schema(
    description = "License aggregation on a set of results."
  ) license: Option[DisplayAggregation[DisplayLicense]],
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Aggregations"
)

@Schema(
  name = "Aggregation",
  description = "An aggregation over the results."
)
case class DisplayAggregation[T](
  @Schema(description = "An aggregation on a set of results") buckets: List[
    DisplayAggregationBucket[T]],
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Aggregation"
)

@Schema(
  name = "AggregationBucket",
  description = "An individual bucket within an aggregation."
)
case class DisplayAggregationBucket[T](
  @Schema(
    description = "The data that this aggregation is of."
  ) data: T,
  @Schema(
    description =
      "The count of how often this data occurs in this set of results."
  ) count: Int,
  @JsonKey("type") @Schema(name = "type") ontologyType: String =
    "AggregationBucket")

object DisplayAggregations {

  implicit def encoder: Encoder[DisplayAggregations] = deriveEncoder

  def apply(aggs: Aggregations): DisplayAggregations =
    DisplayAggregations(
      workType = displayAggregation(aggs.workType, DisplayWorkType.apply),
      productionDates =
        displayAggregation(aggs.productionDates, DisplayPeriod.apply),
      genres = displayAggregation[Genre[Minted], DisplayGenre](
        aggs.genres,
        DisplayGenre(_, false)),
      language = displayAggregation(aggs.language, DisplayLanguage.apply),
      subjects = displayAggregation[Subject[Minted], DisplaySubject](
        aggs.subjects,
        subject => DisplaySubject(subject, false)
      ),
      license = displayAggregation(aggs.license, DisplayLicense.apply),
    )

  private def displayAggregation[T, D](
    maybeAgg: Option[Aggregation[T]],
    display: T => D): Option[DisplayAggregation[D]] =
    maybeAgg.map { agg =>
      DisplayAggregation(
        buckets = agg.buckets.map { bucket =>
          DisplayAggregationBucket(
            data = display(bucket.data),
            count = bucket.count
          )
        }
      )
    }
}
