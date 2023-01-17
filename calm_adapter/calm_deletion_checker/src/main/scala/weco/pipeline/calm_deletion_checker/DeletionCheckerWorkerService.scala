package weco.pipeline.calm_deletion_checker

import akka.Done
import akka.stream.scaladsl.{Flow, Keep}
import software.amazon.awssdk.services.sqs.model.Message
import weco.messaging.MessageSender
import weco.messaging.sns.NotificationMessage
import weco.messaging.sqs.SQSStream
import weco.json.JsonUtil.fromJson
import weco.typesafe.Runnable
import weco.catalogue.source_model.CalmSourcePayload
import weco.catalogue.source_model.Implicits._
import weco.pipeline.calm_api_client.CalmApiClient

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Success

class DeletionCheckerWorkerService[Destination](
  messageStream: SQSStream[NotificationMessage],
  messageSender: MessageSender[Destination],
  markDeleted: DeletionMarker,
  calmApiClient: CalmApiClient,
  batchSize: Int,
  batchDuration: FiniteDuration = 3 minutes
)(implicit ec: ExecutionContext)
    extends Runnable {

  private val className = this.getClass.getSimpleName
  private val parallelism = 5

  def run(): Future[Done] = messageStream.runGraph(className) {
    (source, sink) =>
      source
        .via(parseBody)
        .divertTo(
          sink.contramap(Keep.left.tupled),
          Keep.right.tupled.andThen(_.isDeleted)
        )
        .groupedWithin(batchSize, batchDuration)
        .via(checkDeletions)
        .mapConcat(identity)
        .via(updateSourceData)
        .toMat(sink)(Keep.right)
  }

  private def parseBody =
    Flow[(Message, NotificationMessage)]
      .mapAsyncUnordered(parallelism) {
        case (msg, NotificationMessage(body)) =>
          Future
            .fromTry(fromJson[CalmSourcePayload](body))
            .map(payload => (msg, payload))
      }

  private def checkDeletions =
    Flow[immutable.Seq[(Message, CalmSourcePayload)]]
      .mapAsyncUnordered(parallelism) { messages =>
        val (_, batch) = messages.unzip
        deletionChecker.defectiveRecords(batch.toSet).map { deleted =>
          messages.map {
            case (m, record) if deleted.contains(record) => (m, record, Deleted)
            case (m, record)                             => (m, record, Extant)
          }
        }
      }

  private def updateSourceData =
    Flow[(Message, CalmSourcePayload, DeletionStatus)]
      .map {
        case (msg, record, Deleted) =>
          markDeleted(record)
            .flatMap(messageSender.sendT[CalmSourcePayload])
            .map(_ => msg)
        case (msg, _, Extant) => Success(msg)
      }
      .map(_.get) // Let akka-streams handle the thrown exception

  private lazy val deletionChecker = new ApiDeletionChecker(calmApiClient)

  sealed trait DeletionStatus extends Product with Serializable
  case object Deleted extends DeletionStatus
  case object Extant extends DeletionStatus

}
