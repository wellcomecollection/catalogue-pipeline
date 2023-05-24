package weco.catalogue.internal_model.fixtures.elasticsearch

import org.elasticsearch.client.{Request, Response}
import com.sksamuel.elastic4s.Index
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import weco.fixtures.{fixture, Fixture, RandomGenerators}
import weco.catalogue.internal_model.matchers.JsonStringIgnoringNullsMatcher

trait ElasticsearchFixtures
    extends RandomGenerators
    with Eventually
    with ScalaFutures
    // Including IntegrationPatience here will save test authors a degree of heartache.
    // It isn't strictly proper to include IntegrationPatience here, as this trait never waits for anything.
    // However, it is generally expected that if you are using this fixture you will be doing some ES CRUD, and
    // then you will also want to ask ES if it has the data you now expect.  That will nondeterministically fail unless
    // there are adequate timeouts.
    with IntegrationPatience
    with JsonStringIgnoringNullsMatcher {

  protected val restClient: RestClient = RestClient
    .builder(new HttpHost("localhost", 9200, "http"))
    .setCompressionEnabled(true)
    .build()

  protected def createIndexName: String =
    s"index-${randomAlphanumeric().toLowerCase}"

  def httpPut(index: String, config: String)(
  ): Response = {
    val rq = new Request("PUT", index)
    rq.setJsonEntity(config)
    restClient.performRequest(rq)
  }

  def httpDelete(
    index: String
  ): Response = {
    restClient.performRequest(new Request("DELETE", index))
  }

  def withLocalElasticSearchIndex[R](
    config: String,
    index: String = createIndexName
  ): Fixture[Index, R] = fixture[Index, R](
    create = {
      httpPut(index, config)
      Index(index)
    },
    destroy = {
      _ =>
        httpDelete(index)
    }
  )

}
