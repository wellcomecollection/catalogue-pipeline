package weco.pipeline.calm_indexer.services

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.{ElasticClient, Index}
import io.circe.Json
import weco.catalogue.source_model.CalmSourcePayload
import weco.catalogue.source_model.calm.CalmRecord
import weco.catalogue.source_model.Implicits._
import weco.elasticsearch.ElasticsearchIndexCreator
import weco.json.JsonUtil.fromJson
import weco.messaging.sns.NotificationMessage
import weco.messaging.sqs.SQSStream
import weco.pipeline.calm_indexer.index.CalmIndexConfig
import weco.storage.Identified
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.Readable
import weco.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

class Worker(
  sqsStream: SQSStream[NotificationMessage],
  calmReader: Readable[S3ObjectLocation, CalmRecord],
  index: Index
)(implicit ec: ExecutionContext, elasticClient: ElasticClient)
    extends Runnable {

  override def run(): Future[Any] =
    for {
      _ <- new ElasticsearchIndexCreator(
        elasticClient,
        index = index,
        config = CalmIndexConfig()
      ).create

      _ <- sqsStream.foreach("Calm indexer", processMessage)
    } yield ()

  def processMessage(notificationMessage: NotificationMessage): Future[Unit] =
    for {
      payload <- Future.fromTry(
        fromJson[CalmSourcePayload](notificationMessage.body)
      )

      _ <-
        if (payload.isDeleted) {
          deleteRecord(payload)
        } else {
          indexRecord(payload)
        }
    } yield ()

  private def deleteRecord(payload: CalmSourcePayload): Future[Unit] =
    elasticClient
      .execute(
        deleteById(index, payload.id)
      )
      .map {
        _ =>
          ()
      }

  private def indexRecord(payload: CalmSourcePayload): Future[Unit] =
    for {
      record <- calmReader.get(payload.location) match {
        case Right(Identified(_, transformable)) =>
          Future.successful(transformable)
        case Left(err) => Future.failed(err.e)
      }

      // We tweak the record coming out of Calm in two ways:
      //
      //    - If the value is empty, it actually gets a list of empty string [""].
      //      We remove this to make it easier to filter records in Elasticsearch.
      //    - If the value is a list with a single value, we unwrap the list.
      fields = record.data
        .filterNot { case (_, value) => value == Seq("") }
        .map {
          case (key, Seq(value)) => key -> Json.fromString(value)
          case (key, value) =>
            key -> Json.fromValues(value.map(Json.fromString))
        }
        .toSeq

      _ <- elasticClient.execute(
        indexInto(index)
          .id(payload.id)
          .source(Json.fromFields(fields).noSpaces)
      )
    } yield ()
}
