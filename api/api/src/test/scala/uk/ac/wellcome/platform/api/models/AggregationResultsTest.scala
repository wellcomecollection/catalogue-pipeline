package uk.ac.wellcome.platform.api.models

import com.sksamuel.elastic4s.requests.common.Shards
import com.sksamuel.elastic4s.requests.searches.{
  SearchHits,
  SearchResponse,
  Total
}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.work.Format
import weco.catalogue.internal_model.work.Format._

class AggregationResultsTest extends AnyFunSpec with Matchers {
  it("destructures a single aggregation result") {
    val searchResponse = SearchResponse(
      took = 1234,
      isTimedOut = false,
      isTerminatedEarly = false,
      suggest = Map(),
      _shards = Shards(total = 1, failed = 0, successful = 1),
      scrollId = None,
      hits = SearchHits(
        total = Total(0, "potatoes"),
        maxScore = 0.0,
        hits = Array()),
      _aggregationsAsMap = Map(
        "format" -> Map(
          "doc_count_error_upper_bound" -> 0,
          "sum_other_doc_count" -> 0,
          "buckets" -> List(
            Map(
              "key" -> "a",
              "doc_count" -> 393145
            ),
            Map(
              "key" -> "b",
              "doc_count" -> 5696
            ),
            Map(
              "key" -> "c",
              "doc_count" -> 9
            )
          )
        )
      )
    )
    val singleAgg = WorkAggregations(searchResponse)
    singleAgg.get.format shouldBe Some(
      Aggregation[Format](
        List(
          AggregationBucket(data = Books, count = 393145),
          AggregationBucket(data = ManuscriptsAsian, count = 5696),
          AggregationBucket(data = Music, count = 9)
        )))
  }

  it("uses the filtered count for aggregations with a filter subaggregation") {
    val searchResponse = SearchResponse(
      took = 1234,
      isTimedOut = false,
      isTerminatedEarly = false,
      suggest = Map(),
      _shards = Shards(total = 1, failed = 0, successful = 1),
      scrollId = None,
      hits = SearchHits(
        total = Total(0, "potatoes"),
        maxScore = 0.0,
        hits = Array()),
      _aggregationsAsMap = Map(
        "format" -> Map(
          "doc_count_error_upper_bound" -> 0,
          "sum_other_doc_count" -> 0,
          "buckets" -> List(
            Map(
              "key" -> "a",
              "doc_count" -> 393145,
              "filtered" -> Map(
                "doc_count" -> 1234
              )
            )
          )
        )
      )
    )
    val singleAgg = WorkAggregations(searchResponse)
    singleAgg.get.format shouldBe Some(
      Aggregation[Format](List(AggregationBucket(data = Books, count = 1234))))
  }

  it("uses the buckets from the global aggregation when present") {
    val searchResponse = SearchResponse(
      took = 1234,
      isTimedOut = false,
      isTerminatedEarly = false,
      suggest = Map(),
      _shards = Shards(total = 1, failed = 0, successful = 1),
      scrollId = None,
      hits = SearchHits(
        total = Total(0, "potatoes"),
        maxScore = 0.0,
        hits = Array()),
      _aggregationsAsMap = Map(
        "format" -> Map(
          "doc_count" -> 12345,
          "format" -> Map(
            "doc_count_error_upper_bound" -> 0,
            "sum_other_doc_count" -> 0,
            "buckets" -> List(
              Map(
                "key" -> "a",
                "doc_count" -> 393145,
                "filtered" -> Map(
                  "doc_count" -> 1234
                )
              )
            )
          )
        )
      )
    )
    val singleAgg = WorkAggregations(searchResponse)
    singleAgg.get.format shouldBe Some(
      Aggregation[Format](List(AggregationBucket(data = Books, count = 1234))))
  }

  it("sorts the buckets by count (in descending order)") {
    val searchResponse = SearchResponse(
      took = 1234,
      isTimedOut = false,
      isTerminatedEarly = false,
      suggest = Map(),
      _shards = Shards(total = 1, failed = 0, successful = 1),
      scrollId = None,
      hits = SearchHits(
        total = Total(0, "potatoes"),
        maxScore = 0.0,
        hits = Array()),
      _aggregationsAsMap = Map(
        "format" -> Map(
          "doc_count" -> 12345,
          "format" -> Map(
            "doc_count_error_upper_bound" -> 0,
            "sum_other_doc_count" -> 0,
            "buckets" -> List(
              Map(
                "key" -> "d",
                "doc_count" -> 10,
                "filtered" -> Map(
                  "doc_count" -> 1
                )
              ),
              Map(
                "key" -> "c",
                "doc_count" -> 9,
                "filtered" -> Map(
                  "doc_count" -> 2
                )
              ),
              Map(
                "key" -> "b",
                "doc_count" -> 8,
                "filtered" -> Map(
                  "doc_count" -> 3
                )
              ),
              Map(
                "key" -> "a",
                "doc_count" -> 7,
                "filtered" -> Map(
                  "doc_count" -> 4
                )
              )
            )
          )
        )
      )
    )
    val singleAgg = WorkAggregations(searchResponse)
    singleAgg.get.format
      .flatMap(_.buckets.headOption)
      .get shouldBe AggregationBucket(data = Books, count = 4)
    singleAgg.get.format
      .map(_.buckets.map(_.count))
      .get
      .reverse shouldBe sorted
  }
}
