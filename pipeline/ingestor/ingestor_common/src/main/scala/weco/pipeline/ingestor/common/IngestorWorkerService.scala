package weco.pipeline.ingestor.common

import scala.concurrent.{ExecutionContext, Future}
import akka.Done
import akka.stream.scaladsl.Flow
import software.amazon.awssdk.services.sqs.model.Message
import weco.messaging.sns.NotificationMessage
import weco.pipeline_storage.PipelineStorageStream.{
  batchRetrieveFlow,
  processFlow
}
import weco.typesafe.Runnable
import weco.pipeline_storage.{Indexable, PipelineStorageStream, Retriever}

class IngestorWorkerService[Destination, In, Out](
  pipelineStream: PipelineStorageStream[NotificationMessage, Out, Destination],
  retriever: Retriever[In],
  transform: In => Out)(implicit ec: ExecutionContext,
                        indexable: Indexable[Out])
    extends Runnable {

  def run(): Future[Done] =
    pipelineStream.run(
      this.getClass.getSimpleName,
      Flow[(Message, NotificationMessage)]
        .via(batchRetrieveFlow(pipelineStream.config, retriever))
        .via(processFlow(pipelineStream.config, item => processMessage(item)))
    )

  private def processMessage(item: In): Future[List[Out]] =
    Future.successful(List(transform(item)))

}
