package uk.ac.wellcome.platform.calm_deletion_checker

import akka.Done
import akka.stream.scaladsl.Flow
import software.amazon.awssdk.services.sqs.model.Message
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.calm_api_client.{CalmRecord, CalmRetriever}
import uk.ac.wellcome.typesafe.Runnable
import weco.catalogue.source_model.CalmSourcePayload
import weco.catalogue.source_model.store.SourceVHS

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.Try

class DeletionCheckerWorkerService[Destination](
  msgStream: SQSStream[NotificationMessage],
  messageSender: MessageSender[Destination],
  calmVHS: SourceVHS[CalmRecord],
  calmRetriever: CalmRetriever,
  batchSize: Int)(implicit ec: ExecutionContext)
    extends Runnable {

  private val className = this.getClass.getSimpleName
  private val batchDuration = 3 minutes
  private val parallelism = 5

  def run(): Future[Done] = msgStream.runStream(
    className,
    source =>
      source
        .via(parseBody)
        .groupedWithin(batchSize, batchDuration)
        .via(checkDeletions)
        .mapConcat(identity)
        .via(updateSourceData)
  )

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
        deletionChecker.deletedRecords(batch).map { deleted =>
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
            .fromTry(markRecordDeleted(record))
            .map(messageSender.sendT)
            .map(_ => msg)
        case (msg, _, Extant) => Future.successful(msg)
      }

  private def markRecordDeleted(
    record: CalmSourcePayload): Try[CalmSourcePayload] = ???

  private lazy val deletionChecker = new DeletionChecker(calmRetriever)

  sealed trait DeletionStatus extends Product with Serializable
  case object Deleted extends DeletionStatus
  case object Extant extends DeletionStatus

}
