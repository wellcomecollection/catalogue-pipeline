package uk.ac.wellcome.platform.api.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.internal.Period

class DateAggregationMergerTest extends AnyFunSpec with Matchers {

  it("aggregates by decade when too many buckets") {
    val aggregation = Aggregation(
      List(
        AggregationBucket(Period("1954"), 2),
        AggregationBucket(Period("1958"), 1),
        AggregationBucket(Period("1960"), 1),
        AggregationBucket(Period("1969"), 5),
        AggregationBucket(Period("1982"), 4),
        AggregationBucket(Period("1983"), 2),
      )
    )
    DateAggregationMerger(aggregation, maxBuckets = 5) shouldBe Aggregation(
      List(
        AggregationBucket(Period("1950-1959"), 3),
        AggregationBucket(Period("1960-1969"), 6),
        AggregationBucket(Period("1980-1989"), 6),
      )
    )
  }

  it("aggregates by half century when too many buckets") {
    val aggregation = Aggregation(
      List(
        AggregationBucket(Period("1898"), 2),
        AggregationBucket(Period("1900"), 1),
        AggregationBucket(Period("1940"), 1),
        AggregationBucket(Period("1958"), 5),
        AggregationBucket(Period("1960"), 1),
        AggregationBucket(Period("1969"), 5),
        AggregationBucket(Period("1982"), 4),
        AggregationBucket(Period("1983"), 2),
      )
    )
    DateAggregationMerger(aggregation, maxBuckets = 5) shouldBe Aggregation(
      List(
        AggregationBucket(Period("1850-1899"), 2),
        AggregationBucket(Period("1900-1949"), 2),
        AggregationBucket(Period("1950-1999"), 17),
      )
    )
  }

  it("aggregates by century when too many buckets") {
    val aggregation = Aggregation(
      List(
        AggregationBucket(Period("1409"), 1),
        AggregationBucket(Period("1608"), 1),
        AggregationBucket(Period("1740"), 1),
        AggregationBucket(Period("1798"), 4),
        AggregationBucket(Period("1800"), 4),
        AggregationBucket(Period("1824"), 4),
        AggregationBucket(Period("1934"), 1),
        AggregationBucket(Period("2012"), 3),
      )
    )
    DateAggregationMerger(aggregation, maxBuckets = 5) shouldBe Aggregation(
      List(
        AggregationBucket(Period("1400-1499"), 1),
        AggregationBucket(Period("1600-1699"), 1),
        AggregationBucket(Period("1700-1799"), 5),
        AggregationBucket(Period("1800-1899"), 8),
        AggregationBucket(Period("1900-1999"), 1),
        AggregationBucket(Period("2000-2099"), 3),
      )
    )
  }
}
