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
import weco.catalogue.sierra_adapter.models.SierraTransformable
import weco.catalogue.source_model.SierraSourcePayload

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

class Worker[MsgDestination](
  sqsStream: SQSStream[NotificationMessage],
  sierraReadable: Readable[S3ObjectLocation, SierraTransformable],
)(
  implicit
  ec: ExecutionContext,
  elasticClient: ElasticClient
) extends Runnable {

  private val splitter = new Splitter(indexPrefix = "sierra")

  override def run(): Future[Any] =
    sqsStream.foreach("Sierra indexer", processMessage)

  def processMessage(notificationMessage: NotificationMessage): Future[Unit] = {
    val ops =
      for {
        payload <- fromJson[SierraSourcePayload](notificationMessage.body)

        transformable <- sierraReadable.get(payload.location) match {
          case Right(Identified(_, transformable)) => Success(transformable)
          case Left(err) => Failure(err.e)
        }

        ops <- splitter.split(transformable) match {
          case Right(ops) => Success(ops)
          case Left(err) => Failure(new Throwable(s"Couldn't get the Elastic requests: $err"))
        }
      } yield ops

    Future.fromTry(ops)
      .flatMap { case (indexRequests, deleteByQueryRequests) =>
        val futures = List(
          elasticClient.execute(
            bulk(indexRequests)
          )
        ) ++ deleteByQueryRequests.map { elasticClient.execute(_) }

        Future.sequence(futures)
      }
      .map { _ => () }
  }
}

