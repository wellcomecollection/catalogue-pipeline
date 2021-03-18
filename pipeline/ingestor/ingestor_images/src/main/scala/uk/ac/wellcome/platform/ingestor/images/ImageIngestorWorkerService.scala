package uk.ac.wellcome.platform.ingestor.images

import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.pipeline_storage.{PipelineStorageStream, Retriever}
import uk.ac.wellcome.platform.ingestor.common.IngestorWorkerService
import weco.catalogue.internal_model.image.Image
import weco.catalogue.internal_model.image.ImageState.{Augmented, Indexed}

import scala.concurrent.ExecutionContext

class ImageIngestorWorkerService[Destination](
  pipelineStream: PipelineStorageStream[NotificationMessage,
                                        Image[Indexed],
                                        Destination],
  imageRetriever: Retriever[Image[Augmented]],
  transform: Image[Augmented] => Image[Indexed] = ImageTransformer.deriveData,
)(implicit
  ec: ExecutionContext)
    extends IngestorWorkerService[
      Destination,
      Image[Augmented],
      Image[Indexed]](pipelineStream, imageRetriever, transform)
