package uk.ac.wellcome.platform.ingestor.images

import uk.ac.wellcome.messaging.MessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.work.internal.ImageState.{Augmented, Indexed}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.{Indexer, PipelineStorageConfig, Retriever}
import uk.ac.wellcome.platform.ingestor.common.IngestorWorkerService

import scala.concurrent.ExecutionContext

class ImageIngestorWorkerService[Destination](
                                               msgStream: SQSStream[NotificationMessage],
                                               indexer: Indexer[Image[Indexed]],
                                               config: PipelineStorageConfig,
                                               messageSender: MessageSender[Destination],
  imageRetriever: Retriever[Image[Augmented]],
  transform: Image[Augmented] => Image[Indexed] = ImageTransformer.deriveData,
)(implicit
  ec: ExecutionContext)
    extends IngestorWorkerService[
      Destination,
      Image[Augmented],
      Image[Indexed]](msgStream, indexer, config, messageSender, imageRetriever, transform)
