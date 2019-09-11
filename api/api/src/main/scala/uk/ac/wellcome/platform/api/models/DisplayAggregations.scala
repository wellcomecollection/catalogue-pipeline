package uk.ac.wellcome.platform.api.models

import com.fasterxml.jackson.annotation.JsonProperty
import io.circe.generic.extras.JsonKey
import io.swagger.annotations.{ApiModel, ApiModelProperty}
import uk.ac.wellcome.display.models.DisplayWorkType

@ApiModel(
  value = "AggregationMap",
  description = "A map of the different aggregations on the ResultList."
)
case class DisplayAggregations(
  @ApiModelProperty(
    value = "WorkType aggregation on a set of results."
  ) workType: Option[DisplayAggregation[DisplayWorkType]],
  @ApiModelProperty(
    value = "Genre aggregation on a set of results."
  ) genre: Option[DisplayAggregation[Genre]],
  @JsonProperty("type") @JsonKey("type") ontologyType: String = "Aggregations"
)

case class DisplayAggregation[T](
  @ApiModelProperty(
    value = "An aggregation on a set of results"
  ) buckets: List[DisplayAggregationBucket[T]],
  @JsonProperty("type") @JsonKey("type") ontologyType: String = "Aggregation")

case class DisplayAggregationBucket[T](
  @ApiModelProperty(
    value = "The data that this aggregation is of."
  ) data: T,
  @ApiModelProperty(
    value = "The count of how often this data occurs in this set of results."
  ) count: Int,
  @JsonProperty("type") @JsonKey("type") ontologyType: String =
    "AggregationBucket")

case object DisplayAggregations {
  // We've done all this here as we need the end type e.g. WorkType to be converted to
  // DisplayWorkType. TODO: Some sort of auto-derivation
  def apply(aggregationMap: Aggregations): DisplayAggregations =
    DisplayAggregations(
      workType = aggregationMap.workType.map(
        aggregation =>
          DisplayAggregation(
            buckets = aggregation.buckets.map(bucket =>
              DisplayAggregationBucket(
                data = DisplayWorkType(bucket.data),
                count = bucket.count)))),
      genre = None
    )
}
