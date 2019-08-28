package uk.ac.wellcome.platform.api.models

import org.scalatest.{FunSpec, Matchers}

class AggregationResultsTest extends FunSpec with Matchers {
  it("destructures a single aggregation result") {
    // val searchResponse: com.sksamuel.elastic4s.requests.searches.SearchResponse

    // This mimics the behaviour of searchResponse.aggregationsAsMap
    val singleAggregationsMap = Map("workType" -> Map.empty)

    //  And this mimics the searchResponse.aggregationsAsString
    val responseString =
      """
        |{"workType":{"doc_count_error_upper_bound":0,"sum_other_doc_count":0,"buckets":[{"key":"b","doc_count":2},{"key":"a","doc_count":1},{"key":"m","doc_count":1}]}}
        |""".stripMargin

    val singleAgg = AggregationResults(singleAggregationsMap, responseString)
    singleAgg.get.workType shouldBe 1
  }
}
