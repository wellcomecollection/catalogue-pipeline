package uk.ac.wellcome.platform.api.models

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.internal.WorkType

class AggregationResultsTest extends FunSpec with Matchers {
  it("destructures a single aggregation result") {
    // val searchResponse: com.sksamuel.elastic4s.requests.searches.SearchResponse

    // This mimics the behaviour of searchResponse.aggregationsAsMap
    val singleAggregationsMap = Map("workType" -> Map.empty)

    //  And this mimics the searchResponse.aggregationsAsString
    val responseString =
      """
        |{
        |  "workType": {
        |    "doc_count_error_upper_bound": 0,
        |    "sum_other_doc_count": 0,
        |    "buckets": [
        |      {
        |          "key" : {
        |            "id" : "a",
        |            "label" : "Books",
        |            "type" : "WorkType"
        |          },
        |          "doc_count" : 393145
        |        },
        |        {
        |          "key" : {
        |            "id" : "b",
        |            "label" : "Manuscripts, Asian",
        |            "type" : "WorkType"
        |          },
        |          "doc_count" : 5696
        |        },
        |        {
        |          "key" : {
        |            "id" : "c",
        |            "label" : "Music",
        |            "type" : "WorkType"
        |          },
        |          "doc_count" : 9
        |        }
        |    ]
        |  }
        |}
        |""".stripMargin

    val singleAgg = AggregationResults(singleAggregationsMap, responseString)
    singleAgg.get.workType shouldBe Some(
      AggregationBuckets(List(
        AggregationBucket(key = WorkType("a", "Books"), doc_count = 393145),
        AggregationBucket(
          key = WorkType("b", "Manuscripts, Asian"),
          doc_count = 5696),
        AggregationBucket(key = WorkType("c", "Music"), doc_count = 9)
      )))
  }
}
