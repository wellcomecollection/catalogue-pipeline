package uk.ac.wellcome.platform.api.models

import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.models.work.internal.WorkType

class AggregationResultsTest extends FunSpec with Matchers {
  it("destructures a single aggregation result") {
    // val searchResponse: com.sksamuel.elastic4s.requests.searches.SearchResponse
    // This mimics the searchResponse.aggregationsAsString
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

    val singleAgg = AggregationSet(responseString)
    singleAgg.workType shouldBe Some(
      Aggregation(List(
        AggregationBucket(data = WorkType("a", "Books"), count = 393145),
        AggregationBucket(
          data = WorkType("b", "Manuscripts, Asian"),
          count = 5696),
        AggregationBucket(data = WorkType("c", "Music"), count = 9)
      )))
  }
}
