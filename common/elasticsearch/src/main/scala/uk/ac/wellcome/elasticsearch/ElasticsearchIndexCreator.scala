package uk.ac.wellcome.elasticsearch

import com.sksamuel.elastic4s.ElasticApi.createIndex
import com.sksamuel.elastic4s.{ElasticClient, Index, Response}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.mappings.dynamictemplate.DynamicMapping
import grizzled.slf4j.Logging
import scala.concurrent.{ExecutionContext, Future}

class ElasticsearchIndexCreator(
  elasticClient: ElasticClient,
  index: Index,
  config: IndexConfig)(implicit ec: ExecutionContext)
    extends Logging {

  def create: Future[Unit] = createOrUpdate

  val mapping = config.mapping
  val analysis = config.analysis

  private def exists =
    elasticClient.execute(indexExists(index.name)).map(_.result.isExists)

  private def createOrUpdate: Future[Unit] = {
    for {
      doesExist <- exists
      createResp <- if (doesExist) update else put
    } yield { handleEsError(createResp) }
  }

  private def put =
    elasticClient
      .execute {
        createIndex(index.name)
          .mapping(mapping)
          .shards(config.shards)
          .analysis(analysis)
          // Elasticsearch has a default maximum number of fields of 1000.
          // Because images have all of the WorkData fields defined twice in the mapping,
          // they end up having more than 1000 fields, so we increase them to 2000
          .settings(Map("mapping.total_fields.limit" -> 2000))
      }

  private def update =
    elasticClient
      .execute(
        putMapping(index.name)
          .dynamic(mapping.dynamic.getOrElse(DynamicMapping.Strict))
          .as(mapping.fields)
      )

  private def handleEsError[T](resp: Response[T]) =
    if (resp.isError) {
      throw new RuntimeException(
        s"Index creation error on index:${index.name} resp: $resp")
    }
}
