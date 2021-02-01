package uk.ac.wellcome.platform.calm_deletion_checker

import akka.{Done, NotUsed}
import akka.stream.scaladsl.{Flow}
import software.amazon.awssdk.services.sqs.model.Message
import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.calm_api_client.CalmRetriever
import uk.ac.wellcome.typesafe.Runnable
import weco.catalogue.source_model.CalmSourcePayload

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

class DeletionCheckerWorkerService[Destination](
  msgStream: SQSStream[NotificationMessage],
  messageSender: MessageSender[Destination],
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

  private def checkDeletions
    : Flow[immutable.Seq[(Message, CalmSourcePayload)],
           immutable.Seq[(Message, CalmSourcePayload, DeletionStatus)],
           NotUsed] = ???

  private def updateSourceData
    : Flow[(Message, CalmSourcePayload, DeletionStatus), Message, NotUsed] = ???

}
