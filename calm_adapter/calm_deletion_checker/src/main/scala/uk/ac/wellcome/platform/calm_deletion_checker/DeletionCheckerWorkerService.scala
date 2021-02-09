package uk.ac.wellcome.platform.calm_deletion_checker

import akka.Done
import akka.stream.scaladsl.{Flow, Keep}
import software.amazon.awssdk.services.sqs.model.Message
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.calm_api_client.CalmApiClient
import uk.ac.wellcome.typesafe.Runnable
import weco.catalogue.source_model.CalmSourcePayload

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class DeletionCheckerWorkerService[Destination](
  msgStream: SQSStream[NotificationMessage],
  messageSender: MessageSender[Destination],
  markDeleted: DeletionMarker,
  calmApiClient: CalmApiClient,
  batchSize: Int)(implicit ec: ExecutionContext)
    extends Runnable {

  private val className = this.getClass.getSimpleName
  private val batchDuration = 3 minutes
  private val parallelism = 5

  def run(): Future[Done] = msgStream.runGraph(className) { (source, sink) =>
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
        deletionChecker.deletedRecords(batch.toSet).map { deleted =>
          messages.map {
            case (m, record) if deleted.contains(record) => (m, record, Deleted)
            case (m, record)                             => (m, record, Extant)
          }
        }
      }

  private def updateSourceData =
    Flow[(Message, CalmSourcePayload, DeletionStatus)]
      .mapAsyncUnordered(parallelism) {
        case (msg, record, Deleted) =>
          Future
            .fromTry(markDeleted(record))
            .map(messageSender.sendT[CalmSourcePayload])
            .map(_ => msg)
        case (msg, _, Extant) => Future.successful(msg)
      }

  private lazy val deletionChecker = new ApiDeletionChecker(calmApiClient)

  sealed trait DeletionStatus extends Product with Serializable
  case object Deleted extends DeletionStatus
  case object Extant extends DeletionStatus

}
