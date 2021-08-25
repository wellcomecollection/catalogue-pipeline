package weco.pipeline.ingestor.images

import scala.concurrent.ExecutionContext.Implicits.global
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.time.{Seconds, Span}
import weco.catalogue.internal_model.index.{ImagesIndexConfig, IndexFixtures}
import weco.messaging.fixtures.SQS.QueuePair
import weco.catalogue.internal_model.Implicits._
import weco.pipeline_storage.Indexable.imageIndexable
import weco.catalogue.internal_model.image.ImageState.{Augmented, Indexed}
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.image.Image
import weco.pipeline.ingestor.fixtures.IngestorFixtures
import weco.pipeline_storage.elastic.{ElasticIndexer, ElasticSourceRetriever}

import scala.concurrent.duration._

class ImagesIngestorFeatureTest
    extends AnyFunSpec
    with ImageGenerators
    with IndexFixtures
    with IngestorFixtures {
  it("reads an image from the queue, ingests it and deletes the message") {
    val image = createImageData.toAugmentedImage

    withLocalSqsQueuePair(visibilityTimeout = 10.seconds) {
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
              ImagesIndexConfig.ingested)
            withWorkerService(
              queue,
              retriever,
              indexer,
              transform = ImageTransformer.deriveData) { _ =>
              assertElasticsearchEventuallyHasImage[Indexed](
                index,
                ImageTransformer.deriveData(image))

              eventually {
                assertQueueEmpty(queue)
                assertQueueEmpty(dlq)
              }
            }
          }
        }
    }
  }

  it("does not delete a message from the queue if it fails processing it") {
    withLocalSqsQueuePair(visibilityTimeout = 1.second) {
      case QueuePair(queue, dlq) =>
        sendNotificationToSQS(queue = queue, body = "nope")
        withLocalImagesIndex { index =>
          withLocalAugmentedImageIndex { augmentedIndex =>
            val indexer = new ElasticIndexer[Image[Indexed]](
              elasticClient,
              index,
              ImagesIndexConfig.ingested)
            val retriever = new ElasticSourceRetriever[Image[Augmented]](
              elasticClient,
              augmentedIndex
            )
            withWorkerService(
              queue,
              retriever,
              indexer,
              transform = ImageTransformer.deriveData) { _ =>
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
