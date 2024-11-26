package weco.pipeline.relation_embedder

import scala.concurrent.{ExecutionContext, Future}
import org.apache.pekko.Done
import org.apache.pekko.stream.Materializer
import grizzled.slf4j.Logging
import weco.json.JsonUtil._
import weco.messaging.sns.NotificationMessage
import weco.messaging.sqs.SQSStream
import weco.typesafe.Runnable
import weco.pipeline.relation_embedder.models.Batch

class RelationEmbedderWorkerService[MsgDestination](
  sqsStream: SQSStream[NotificationMessage],
  downstream: Downstream,
  relationsService: RelationsService,
  batchWriter: BatchWriter
)(implicit ec: ExecutionContext, materializer: Materializer)
    extends Runnable
    with Logging {
  private val processor = new BatchProcessor(
    relationsService = relationsService,
    batchWriter = batchWriter,
    downstream = downstream
  )

  def run(): Future[Done] =
    sqsStream.foreach(this.getClass.getSimpleName, processMessage)

  def processMessage(message: NotificationMessage): Future[Unit] = {
    val batch = fromJson[Batch](message.body)
    Future
      .fromTry(batch)
      .flatMap {
        batch =>
          processor(batch)
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
