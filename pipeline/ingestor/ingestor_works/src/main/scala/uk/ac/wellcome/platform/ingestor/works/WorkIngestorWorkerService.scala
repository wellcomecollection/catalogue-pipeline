package uk.ac.wellcome.platform.ingestor.works

import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.{PipelineStorageStream, Retriever}
import uk.ac.wellcome.platform.ingestor.common.IngestorWorkerService
import WorkState.{Denormalised, Indexed}

import scala.concurrent.ExecutionContext

class WorkIngestorWorkerService[Destination](
  pipelineStream: PipelineStorageStream[NotificationMessage,
                                        Work[Indexed],
                                        Destination],
  workRetriever: Retriever[Work[Denormalised]],
  transform: Work[Denormalised] => Work[Indexed] = WorkTransformer.deriveData,
)(implicit
  ec: ExecutionContext)
    extends IngestorWorkerService[
      Destination,
      Work[Denormalised],
      Work[Indexed]](pipelineStream, workRetriever, transform)
