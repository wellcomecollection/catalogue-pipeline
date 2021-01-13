package uk.ac.wellcome.platform.ingestor.common

import scala.concurrent.{ExecutionContext, Future}
import akka.Done

import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.pipeline_storage.{PipelineStorageStream, Retriever, Indexable}
import uk.ac.wellcome.typesafe.Runnable

class IngestorWorkerService[Destination, In, Out](
  pipelineStream: PipelineStorageStream[NotificationMessage, Out, Destination],
  workRetriever: Retriever[In],
  transform: In => Out)(
  implicit ec: ExecutionContext, indexable: Indexable[Out])
    extends Runnable {

  def run(): Future[Done] =
    pipelineStream.foreach(this.getClass.getSimpleName, processMessage)

  private def processMessage(message: NotificationMessage): Future[List[Out]] =
    workRetriever.apply(message.body)
      .map { item => List(transform(item)) }
}
