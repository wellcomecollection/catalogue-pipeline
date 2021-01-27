package uk.ac.wellcome.platform.ingestor.works

import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.work.internal.WorkState.{Denormalised, Indexed}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.{Indexer, PipelineStorageConfig, Retriever}
import uk.ac.wellcome.platform.ingestor.common.IngestorWorkerService

import scala.concurrent.ExecutionContext

class WorkIngestorWorkerService[Destination](
                                              msgStream: SQSStream[NotificationMessage],
                                              indexer: Indexer[Work[Indexed]],
                                              config: PipelineStorageConfig,
                                              messageSender: MessageSender[Destination],
  workRetriever: Retriever[Work[Denormalised]],
  transform: Work[Denormalised] => Work[Indexed] = WorkTransformer.deriveData,
)(implicit
  ec: ExecutionContext)
    extends IngestorWorkerService[
      Destination,
      Work[Denormalised],
      Work[Indexed]](msgStream, indexer, config, messageSender, workRetriever, transform)
