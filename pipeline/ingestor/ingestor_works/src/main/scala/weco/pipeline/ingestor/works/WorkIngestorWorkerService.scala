package weco.pipeline.ingestor.works

import weco.messaging.sns.NotificationMessage
import weco.pipeline_storage.PipelineStorageStream
import weco.catalogue.internal_model.work.Work
import weco.catalogue.internal_model.work.WorkState.{Denormalised, Indexed}
import weco.pipeline.ingestor.common.IngestorWorkerService
import weco.pipeline_storage.{PipelineStorageStream, Retriever}

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
