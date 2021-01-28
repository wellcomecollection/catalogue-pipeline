package uk.ac.wellcome.platform.api.models

import io.circe.generic.extras.JsonKey
import io.swagger.v3.oas.annotations.media.Schema

@Schema(
  name = "Aggregation",
  description = "An aggregation over the results."
)
case class DisplayAggregation[T](
  @Schema(description = "An aggregation on a set of results") buckets: List[
    DisplayAggregationBucket[T]],
  @JsonKey("type") @Schema(name = "type") ontologyType: String = "Aggregation"
)

case object DisplayAggregation {
  def apply[T, DisplayT](agg: Aggregation[T], display: T => DisplayT): DisplayAggregation[DisplayT] =
    DisplayAggregation(
      buckets = agg.buckets.map { bucket =>
        DisplayAggregationBucket(
          data = display(bucket.data),
          count = bucket.count
        )
      }
    )
}

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
    "AggregationBucket"
)
