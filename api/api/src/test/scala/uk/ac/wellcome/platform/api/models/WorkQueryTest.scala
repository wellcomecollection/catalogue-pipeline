package uk.ac.wellcome.platform.api.models

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.searches.SearchRequest
import com.sksamuel.elastic4s.requests.searches.queries.Query
import org.scalatest.FunSpec
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.platform.api.models.WorkQuery._

class WorkQueryTest extends FunSpec with ElasticsearchFixtures {
  it("creates a MSMBoostQuery") {
    assertQuery(
      MSMBoostQuery("the query").query(),
      """{"query":{"simple_query_string":{"lenient":"true","minimum_should_match":"60%","fields":["*.*","title^9.0","subjects.*^8.0","genres.label^8.0","description^3.0","contributors.*^2.0"],"flags":"PHRASE","query":"the query"}}}"""
    )
  }

  private def assertQuery(query: Query, expectedJsonQuery: String): Any = {
    val searchRequest: SearchRequest = search("index").query(query)
    elasticClient.show(searchRequest).toString should include(expectedJsonQuery)
  }
}
