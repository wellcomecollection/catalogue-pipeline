package uk.ac.wellcome.platform.ingestor.common

import scala.concurrent.{ExecutionContext, Future}
import akka.Done
import akka.stream.scaladsl.Flow
import software.amazon.awssdk.services.sqs.model.Message
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.pipeline_storage.PipelineStorageStream.{batchRetrieveFlow, processFlow}
import uk.ac.wellcome.pipeline_storage.{Indexable, PipelineStorageStream, Retriever}
import uk.ac.wellcome.typesafe.Runnable

class IngestorWorkerService[Destination, In, Out](
  pipelineStream: PipelineStorageStream[NotificationMessage, Out, Destination],
  workRetriever: Retriever[In],
  transform: In => Out)(implicit ec: ExecutionContext,
                        indexable: Indexable[Out])
    extends Runnable {

  def run(): Future[Done] =
    pipelineStream.run(this.getClass.getSimpleName,
      Flow[(Message, NotificationMessage)]
        .via(batchRetrieveFlow(pipelineStream.config, workRetriever))
        .via(processFlow(pipelineStream.config, item => processMessage(item))))

  private def processMessage(item: In): Future[List[Out]] =
    Future.successful(List(transform(item)))

}
