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

  it("creates a BoostQuery") {
    assertQuery(
      BoostQuery("the query").query(),
      """{"multi_match":{"query":"the query","fields":["*","subjects*^8","genres*^8","title^9","description*^5","contributors*^2"],"type":"cross_fields"}}"""
    )
  }

  it("creates a MSMQuery") {
    assertQuery(
      MSMQuery("the query").query(),
      """{"multi_match":{"query":"the query","fields":["*"],"type":"cross_fields","minimum_should_match":"60%"}}""")
  }
  
  it("creates a MSMBoostQuery") {
    assertQuery(
      MSMBoostQuery("the query").query(),
      """{"multi_match":{"query":"the query","fields":["*","subjects*^8","genres*^8","title^9","description*^5","contributors*^2"],"type":"cross_fields","minimum_should_match":"60%"}}""")
  }

  private def assertQuery(query: Query, expectedJsonQuery: String): Any = {
    val searchRequest: SearchRequest = search("index").query(query)
    elasticClient.show(searchRequest).toString should include(expectedJsonQuery)
  }
}
