package weco.pipeline.sierra_indexer.services

import com.sksamuel.elastic4s.ElasticApi.bulk
import com.sksamuel.elastic4s.{ElasticClient, Index, RequestSuccess}
import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.bulk.BulkResponse
import com.sksamuel.elastic4s.requests.delete.DeleteByQueryResponse
import weco.elasticsearch.ElasticsearchIndexCreator
import weco.json.JsonUtil.fromJson
import weco.messaging.sns.NotificationMessage
import weco.messaging.sqs.SQSStream
import weco.storage.Identified
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.Readable
import weco.typesafe.Runnable
import weco.catalogue.source_model.SierraSourcePayload
import weco.catalogue.source_model.sierra.SierraTransformable
import weco.catalogue.source_model.Implicits._
import weco.pipeline.sierra_indexer.index.SierraIndexConfig

import scala.concurrent.{ExecutionContext, Future}

class Worker(
  sqsStream: SQSStream[NotificationMessage],
  sierraReadable: Readable[S3ObjectLocation, SierraTransformable],
  indexPrefix: String = "sierra"
)(
  implicit
  ec: ExecutionContext,
  elasticClient: ElasticClient
) extends Runnable {

  private val splitter = new Splitter(indexPrefix = indexPrefix)

  override def run(): Future[Any] =
    for {
      _ <- new ElasticsearchIndexCreator(
        elasticClient,
        index = Index(s"${indexPrefix}_varfields"),
        config = SierraIndexConfig.varfield
      ).create

      _ <- new ElasticsearchIndexCreator(
        elasticClient,
        index = Index(s"${indexPrefix}_fixedfields"),
        config = SierraIndexConfig.fixedField
      ).create

      _ <- sqsStream.foreach("Sierra indexer", processMessage)
    } yield ()

  def processMessage(notificationMessage: NotificationMessage): Future[Unit] = {
    val ops =
      for {
        payload <- Future.fromTry(
          fromJson[SierraSourcePayload](notificationMessage.body)
        )

        transformable <- sierraReadable.get(payload.location) match {
          case Right(Identified(_, transformable)) =>
            Future.successful(transformable)
          case Left(err) => Future.failed(err.e)
        }

        ops <- splitter.split(transformable)
      } yield ops

    ops
      .flatMap {
        case (indexRequests, deleteByQueryRequests) =>
          val futures = deleteByQueryRequests.map { elasticClient.execute(_) } :+ elasticClient
            .execute(bulk(indexRequests))

          Future.sequence(futures)
      }
      .map { resp =>
        resp.foreach {
          case RequestSuccess(_, _, _, d: DeleteByQueryResponse) =>
            ()

          case RequestSuccess(_, _, _, b: BulkResponse) =>
            if (b.hasFailures) {
              throw new RuntimeException(s"Errors in the bulk response: $b")
            }

          case _ =>
            ()
        }
      }
  }
}
