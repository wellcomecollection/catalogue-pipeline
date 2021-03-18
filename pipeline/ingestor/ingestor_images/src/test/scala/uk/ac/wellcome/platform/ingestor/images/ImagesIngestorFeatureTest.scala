package uk.ac.wellcome.platform.ingestor.images

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.time.{Seconds, Span}
import uk.ac.wellcome.models.index.{IndexFixtures, IndexedImageIndexConfig}
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.pipeline_storage.ElasticIndexer
import uk.ac.wellcome.pipeline_storage.Indexable.imageIndexable
import weco.catalogue.internal_model.image.ImageState.{Augmented, Indexed}
import uk.ac.wellcome.pipeline_storage.elastic.ElasticSourceRetriever
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.image.Image

class ImagesIngestorFeatureTest
    extends AnyFunSpec
    with ImageGenerators
    with IndexFixtures
    with IngestorFixtures {
  it("reads an image from the queue, ingests it and deletes the message") {
    val image = createImageData.toAugmentedImage

    withLocalSqsQueuePair(visibilityTimeout = 10) {
      case QueuePair(queue, dlq) =>
        sendNotificationToSQS(queue = queue, body = image.id)
        withLocalImagesIndex { index =>
          withLocalAugmentedImageIndex { augmentedIndex =>
            insertImagesIntoElasticsearch(augmentedIndex, image)
            val retriever = new ElasticSourceRetriever[Image[Augmented]](
              elasticClient,
              augmentedIndex
            )
            val indexer = new ElasticIndexer[Image[Indexed]](
              elasticClient,
              index,
              IndexedImageIndexConfig)
            withWorkerService(queue, retriever, indexer) { _ =>
              assertElasticsearchEventuallyHasImage[Indexed](
                index,
                ImageTransformer.deriveData(image))
              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)
            }
          }
        }
    }
  }

  it("does not delete a message from the queue if it fails processing it") {
    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        sendNotificationToSQS(queue = queue, body = "nope")
        withLocalImagesIndex { index =>
          withLocalAugmentedImageIndex { augmentedIndex =>
            val indexer = new ElasticIndexer[Image[Indexed]](
              elasticClient,
              index,
              IndexedImageIndexConfig)
            val retriever = new ElasticSourceRetriever[Image[Augmented]](
              elasticClient,
              augmentedIndex
            )
            withWorkerService(queue, retriever, indexer) { _ =>
              assertElasticsearchEmpty(index)
              eventually(Timeout(Span(5, Seconds))) {
                assertQueueEmpty(queue)
                assertQueueHasSize(dlq, size = 1)
              }
            }
          }
        }
    }
  }
}
