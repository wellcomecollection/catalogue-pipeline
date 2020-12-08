package uk.ac.wellcome.platform.ingestor.works

import akka.Done
import grizzled.slf4j.Logging
import software.amazon.awssdk.services.sqs.model.Message
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.internal.WorkState.{Denormalised, Indexed}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.{PipelineStorageStream, Retriever}
import uk.ac.wellcome.typesafe.Runnable

import scala.concurrent.{ExecutionContext, Future}

class WorkIngestorWorkerService[Destination](
  pipelineStream: PipelineStorageStream[NotificationMessage,
                                        Work[Indexed],
                                        Destination],
  workRetriever: Retriever[Work[Denormalised]],
  transformBeforeIndex: Work[Denormalised] => Work[Indexed] =
    WorkTransformer.deriveData,
)(implicit
  ec: ExecutionContext)
    extends Runnable
    with Logging {

  case class Bundle(message: Message, key: String)

  def run(): Future[Done] =
    pipelineStream.foreach(this.getClass.getSimpleName, processMessage)

  private def processMessage(
    message: NotificationMessage): Future[Option[Work[Indexed]]] = {
    for {
      denormalisedWork <- workRetriever.apply(message.body)
      indexedWork = transformBeforeIndex(denormalisedWork)
    } yield (Some(indexedWork))
  }
}
