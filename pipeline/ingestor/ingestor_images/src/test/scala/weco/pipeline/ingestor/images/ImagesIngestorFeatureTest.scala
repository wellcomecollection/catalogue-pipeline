package weco.pipeline.ingestor.images

import com.sksamuel.elastic4s.ElasticDsl._
import com.sksamuel.elastic4s.requests.get.GetResponse
import com.sksamuel.elastic4s.{Index, Response}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.time.{Seconds, Span}
import weco.catalogue.display_model.image.DisplayImage
import weco.catalogue.internal_model.Implicits._
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.image.ImageState.Augmented
import weco.catalogue.internal_model.image.{Image, ImageState}
import weco.catalogue.internal_model.fixtures.index.IndexFixtures
import weco.fixtures.TestWith
import weco.json.JsonUtil._
import weco.messaging.fixtures.SQS.{Queue, QueuePair}
import weco.pipeline.ingestor.fixtures.IngestorFixtures
import weco.pipeline.ingestor.images.models.{
  ImageAggregatableValues,
  ImageFilterableValues,
  ImageQueryableValues,
  ImageVectorValues,
  IndexedImage
}
import weco.pipeline_storage.elastic.{ElasticIndexer, ElasticSourceRetriever}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class ImagesIngestorFeatureTest
    extends AnyFunSpec
    with ImageGenerators
    with IndexFixtures
    with IngestorFixtures
     {

  it("reads an image from the queue, ingests it and deletes the message") {
    val image = createImageData.toAugmentedImage

    withLocalSqsQueuePair(visibilityTimeout = 10.seconds) {
      case QueuePair(queue, dlq) =>
        sendNotificationToSQS(queue = queue, body = image.id)

        withImagesIngestor(queue, existingImages = Seq(image)) {
          index =>
            eventually {
              val response: Response[GetResponse] = elasticClient.execute {
                get(index, image.id)
              }.await

              val getResponse = response.result

              if (getResponse.sourceAsString == null) {
                warn("Got null when trying to fetch image from Elasticsearch")
              }

              val storedImage =
                fromJson[IndexedImage](getResponse.sourceAsString).get

              storedImage.modifiedTime shouldBe image.modifiedTime

              val storedJson = storedImage.display.as[DisplayImage].right.get
              val expectedJson = DisplayImage(image)

              storedImage.query shouldBe ImageQueryableValues(image)
              storedImage.aggregatableValues shouldBe ImageAggregatableValues(
                image
              )
              storedImage.filterableValues shouldBe ImageFilterableValues(image)
              storedImage.vectorValues shouldBe ImageVectorValues(image)

              storedJson shouldBe expectedJson
            }

            eventually {
              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)
            }
        }
    }
  }

  it("does not delete a message from the queue if it fails processing it") {
    withLocalSqsQueuePair(visibilityTimeout = 1.second) {
      case QueuePair(queue, dlq) =>
        sendNotificationToSQS(queue = queue, body = "nope")

        withImagesIngestor(queue, existingImages = Nil) {
          index =>
            assertElasticsearchEmpty(index)
            eventually(
              timeout(Span(10, Seconds)),
              // The message does not expire until a second has elapsed, so don't bother
              // looking until at least then.
              interval(Span(1, Seconds))
            ) {
              assertQueueEmpty(queue)
              assertQueueHasSize(dlq, size = 1)
            }
        }
    }
  }

  def withImagesIngestor[R](
    queue: Queue,
    existingImages: Seq[Image[ImageState.Augmented]]
  )(testWith: TestWith[Index, R]): R =
    withLocalImagesIndex {
      index =>
        withLocalAugmentedImageIndex {
          augmentedIndex =>
            insertImagesIntoElasticsearch(augmentedIndex, existingImages: _*)

            val retriever = new ElasticSourceRetriever[Image[Augmented]](
              elasticClient,
              augmentedIndex
            )

            val indexer = new ElasticIndexer[IndexedImage](
              elasticClient,
              index
            )

            withWorkerService(
              queue,
              retriever,
              indexer,
              transform = ImageTransformer.deriveData
            ) {
              _ =>
                testWith(index)
            }
        }
    }
}
