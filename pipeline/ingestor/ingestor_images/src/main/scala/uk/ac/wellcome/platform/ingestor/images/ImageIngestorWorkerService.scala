package uk.ac.wellcome.platform.ingestor.images

import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.{PipelineStorageStream, Retriever}
import uk.ac.wellcome.platform.ingestor.common.IngestorWorkerService
import ImageState.{Augmented, Indexed}

import scala.concurrent.ExecutionContext

class ImageIngestorWorkerService[Destination](
  pipelineStream: PipelineStorageStream[NotificationMessage,
                                        Image[Indexed],
                                        Destination],
  imageRetriever: Retriever[Image[Augmented]],
  transform: Image[Augmented] => Image[Indexed] = ImageTransformer.deriveData,
)(implicit
  ec: ExecutionContext)
  extends IngestorWorkerService[Destination, Image[Augmented], Image[Indexed]](
    pipelineStream, imageRetriever, transform)
