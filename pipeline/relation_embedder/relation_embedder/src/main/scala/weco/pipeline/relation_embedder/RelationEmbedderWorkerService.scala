package weco.pipeline.relation_embedder

import scala.concurrent.{ExecutionContext, Future}
import org.apache.pekko.Done
import grizzled.slf4j.Logging
import weco.json.JsonUtil._
import weco.messaging.sns.NotificationMessage
import weco.messaging.sqs.SQSStream
import weco.typesafe.Runnable
import weco.pipeline.relation_embedder.models.Batch

class RelationEmbedderWorkerService[MsgDestination](
  sqsStream: SQSStream[NotificationMessage],
  batchProcessor: BatchProcessor
)(implicit ec: ExecutionContext)
    extends Runnable
    with Logging {

  def run(): Future[Done] =
    sqsStream.foreach(this.getClass.getSimpleName, processMessage)

  def processMessage(message: NotificationMessage): Future[Unit] = {
    val batch = fromJson[Batch](message.body)
    Future
      .fromTry(batch)
      .flatMap {
        batch =>
          batchProcessor(batch)
      }
      .recoverWith {
        case err =>
          val batchString =
            batch.map(_.toString).getOrElse("could not parse message")
          error(s"Failed processing batch: $batchString", err)
          Future.failed(err)
      }
  }
}
