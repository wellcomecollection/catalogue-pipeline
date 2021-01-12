package uk.ac.wellcome.platform.ingestor.images

import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.time.{Seconds, Span}
import uk.ac.wellcome.bigmessaging.fixtures.BigMessagingFixture
import uk.ac.wellcome.elasticsearch.IndexedImageIndexConfig
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.ImageGenerators
import uk.ac.wellcome.models.work.internal.{Image, ImageState}
import uk.ac.wellcome.pipeline_storage.ElasticIndexer
import uk.ac.wellcome.pipeline_storage.Indexable.imageIndexable

import scala.concurrent.ExecutionContext.Implicits.global
import uk.ac.wellcome.json.JsonUtil._

class ImagesIngestorFeatureTest
    extends AnyFunSpec
    with ImageGenerators
    with BigMessagingFixture
    with ElasticsearchFixtures
    with IngestorFixtures {
  it("reads an image from the queue, ingests it and deletes the message") {
    val image = createImageData.toAugmentedImage

    withLocalSqsQueuePair(visibilityTimeout = 10) {
      case QueuePair(queue, dlq) =>
        sendMessage[Image[ImageState.Augmented]](queue = queue, obj = image)
        withLocalImagesIndex { index =>
          val indexer = new ElasticIndexer[Image[ImageState.Augmented]](
            elasticClient,
            index,
            IndexedImageIndexConfig)
          withWorkerService(queue, indexer) { _ =>
            assertElasticsearchEventuallyHasImage[ImageState.Indexed](
              index,
              ImageTransformer.deriveData(image))
            assertQueueEmpty(queue)
            assertQueueEmpty(dlq)
          }
        }
    }
  }

  it("does not delete a message from the queue if it fails processing it") {
    case class Something(something: String, somethingElse: Int)
    val wrongMessage = Something("abcd", 3)

    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        sendMessage[Something](queue = queue, obj = wrongMessage)
        withLocalImagesIndex { index =>
          val indexer = new ElasticIndexer[Image[ImageState.Augmented]](
            elasticClient,
            index,
            IndexedImageIndexConfig)
          withWorkerService(queue, indexer) { _ =>
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
