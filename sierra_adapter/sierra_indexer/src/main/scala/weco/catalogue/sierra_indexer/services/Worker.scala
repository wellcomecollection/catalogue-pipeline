package weco.catalogue.sierra_indexer.services

import com.sksamuel.elastic4s.ElasticApi.bulk
import com.sksamuel.elastic4s.ElasticClient
import com.sksamuel.elastic4s.ElasticDsl._
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.storage.Identified
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.Readable
import uk.ac.wellcome.typesafe.Runnable
import weco.catalogue.source_model.SierraSourcePayload
import weco.catalogue.source_model.sierra.SierraTransformable

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
    sqsStream.foreach("Sierra indexer", processMessage)

  def processMessage(notificationMessage: NotificationMessage): Future[Unit] = {
    val ops =
      for {
        payload <- Future.fromTry(
          fromJson[SierraSourcePayload](notificationMessage.body)
        )

        transformable <- sierraReadable.get(payload.location) match {
          case Right(Identified(_, transformable)) => Future.successful(transformable)
          case Left(err)                           => Future.failed(err.e)
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
      .map { _ =>
        ()
      }
  }
}
