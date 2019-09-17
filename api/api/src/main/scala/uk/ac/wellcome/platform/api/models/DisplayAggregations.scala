package uk.ac.wellcome.platform.api.models

import com.fasterxml.jackson.annotation.JsonProperty
import io.circe.generic.extras.JsonKey
import io.swagger.annotations.{ApiModel, ApiModelProperty}

import uk.ac.wellcome.display.models.DisplayWorkType
import uk.ac.wellcome.display.models.v2.DisplayPeriod

@ApiModel(
  value = "AggregationMap",
  description = "A map of the different aggregations on the ResultList."
)
case class DisplayAggregations(
  @ApiModelProperty(
    value = "WorkType aggregation on a set of results."
  ) workType: Option[DisplayAggregation[DisplayWorkType]],
  @ApiModelProperty(
    value = "Year aggregation on a set of results."
  ) year: Option[DisplayAggregation[DisplayPeriod]],
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

object DisplayAggregations {

  def apply(aggs: Aggregations): DisplayAggregations =
    DisplayAggregations(
      workType = displayAggregation(aggs.workType, DisplayWorkType.apply),
      year = displayAggregation(aggs.year, DisplayPeriod.apply),
      genre = None
    )

  private def displayAggregation[T, D](
    maybeAgg: Option[Aggregation[T]], display: T => D): Option[DisplayAggregation[D]] =
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
