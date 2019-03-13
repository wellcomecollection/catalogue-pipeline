package uk.ac.wellcome.platform.api.models

import com.sksamuel.elastic4s.http.ElasticDsl._
import com.sksamuel.elastic4s.searches.SearchRequest
import com.sksamuel.elastic4s.searches.queries.Query
import org.scalatest.FunSpec
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.platform.api.models.WorkQuery._

class WorkQueryTest extends FunSpec with ElasticsearchFixtures {
  it("creates a SimpleQuery") {
    assertQuery(
      SimpleQuery("the query").query(),
      """{"simple_query_string":{"query":"the query"}}""")
  }

  it("creates a QueryStringQuery") {
    assertQuery(
      QueryString("the query").query(),
      """{"query_string":{"query":"the query"}}""")
  }

  it("creates a JustBoostQuery") {
    assertQuery(
      JustBoostQuery("the query").query(),
      """{"multi_match":{"query":"the query","fields":["*","subjects*^4","genres*^4","title^3"],"type":"cross_fields"}}"""
    )
  }

  it("creates a BroaderBoostQuery") {
    assertQuery(
      BroaderBoostQuery("the query").query(),
      """{"multi_match":{"query":"the query","fields":["*","subjects*^8","genres*^8","title^5","description*^2","lettering*^2","contributors*^2"],"type":"cross_fields"}}"""
    )
  }

  it("creates a SlopQuery") {
    assertQuery(
      SlopQuery("the query").query(),
      """{"multi_match":{"query":"the query","fields":["subjects","genres","title","description","lettering","contributors"],"type":"phrase","slop":3}}"""
    )
  }

  it("creates a MinimumMatchQuery") {
    assertQuery(
      MinimumMatchQuery("the query").query(),
      """{"multi_match":{"query":"the query","fields":["*"],"type":"cross_fields","minimum_should_match":"70%"}}""")
  }

  private def assertQuery(query: Query, expectedJsonQuery: String): Any = {
    val searchRequest: SearchRequest = search("index").query(query)
    elasticClient.show(searchRequest).toString should include(expectedJsonQuery)
  }
}
