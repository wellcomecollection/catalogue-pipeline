package uk.ac.wellcome.platform.api.models

import java.time.LocalDate
import com.sksamuel.elastic4s.ElasticDate
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.searches.queries.{Query, RangeQuery}

sealed trait WorkFilter {

  def query: Query
}

case class ItemLocationTypeFilter(locationTypeIds: Seq[String])
    extends WorkFilter {

  def query =
    termsQuery(
      field = "items.agent.locations.locationType.id",
      values = locationTypeIds)
}

case object ItemLocationTypeFilter {

  def apply(locationTypeId: String): ItemLocationTypeFilter =
    ItemLocationTypeFilter(locationTypeIds = Seq(locationTypeId))
}

case class WorkTypeFilter(workTypeIds: Seq[String]) extends WorkFilter {

  def query =
    termsQuery(field = "workType.id", values = workTypeIds)
}

case object WorkTypeFilter {

  def apply(workTypeId: String): WorkTypeFilter =
    WorkTypeFilter(workTypeIds = Seq(workTypeId))
}

case class DateRangeFilter(fromDate: Option[LocalDate],
                           toDate: Option[LocalDate])
    extends WorkFilter {

  def query = {
    val minDate = getElasticDate(fromDate, Int.MinValue)
    val maxDate = getElasticDate(toDate, Int.MaxValue)

    boolQuery should (
      // Start date of work is within query range
      RangeQuery("production.dates.range.from", gte = minDate, lte = maxDate),
      // End date of work is within query range
      RangeQuery("production.dates.range.to", gte = minDate, lte = maxDate),
      // Work date range is broader than whole query range
      boolQuery must (
        RangeQuery("production.dates.range.from", lte = minDate),
        RangeQuery("production.dates.range.to", gte = maxDate)
      )
    )
  }

  private def getElasticDate(date: Option[LocalDate], default: Int) =
    Some(ElasticDate(date.getOrElse(LocalDate.ofEpochDay(default))))
}
