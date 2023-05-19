package weco.catalogue.internal_model.fixtures.elasticsearch

import org.elasticsearch.client.{Request, Response}
import com.sksamuel.elastic4s.Index
import org.apache.http.HttpHost
import org.elasticsearch.client.RestClient
import weco.fixtures.{fixture, Fixture, RandomGenerators}

trait ElasticsearchFixtures extends RandomGenerators {
  private def createIndexName: String =
    s"index-${randomAlphanumeric().toLowerCase}"

  private val restClient = RestClient
    .builder(new HttpHost("localhost", 9200, "http"))
    .setCompressionEnabled(true)
    .build()

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
