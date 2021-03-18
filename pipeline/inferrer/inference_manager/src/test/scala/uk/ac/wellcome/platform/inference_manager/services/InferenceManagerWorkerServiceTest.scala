package uk.ac.wellcome.platform.inference_manager.services

import scala.collection.mutable
import akka.http.scaladsl.model.{HttpResponse, Uri}
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, Inside, Inspectors, OptionValues}
import software.amazon.awssdk.services.sqs.model.Message
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.work.internal.InferredData
import uk.ac.wellcome.platform.inference_manager.adapters.{
  FeatureVectorInferrerAdapter,
  InferrerAdapter,
  PaletteInferrerAdapter
}
import uk.ac.wellcome.platform.inference_manager.fixtures.{
  InferenceManagerWorkerServiceFixture,
  MemoryFileWriter,
  RequestPoolFixtures,
  RequestPoolMock,
  Responses
}
import uk.ac.wellcome.platform.inference_manager.models.DownloadedImage
import weco.catalogue.internal_model.generators.ImageGenerators
import weco.catalogue.internal_model.image.{Image, ImageState, InferredData}
import weco.catalogue.internal_model.image.ImageState.{Augmented, Initial}

class InferenceManagerWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with ImageGenerators
    with Inside
    with OptionValues
    with Inspectors
    with BeforeAndAfterAll
    with Eventually
    with IntegrationPatience
    with InferenceManagerWorkerServiceFixture
    with RequestPoolFixtures {

  it(
    "reads image messages, augments them with the inferrers, and sends them to SNS") {
    val images = (1 to 5)
      .map(_ => createImageData.toInitialImage)
      .map(image => image.id -> image)
      .toMap
    withResponsesAndFixtures(
      images.values.toList,
      inferrer = req =>
        images.keys
          .find(req.contains(_))
          .flatMap { id =>
            if (req.contains("feature_inferrer")) {
              // Using the ID to seed the random generators means we can make
              // sure that collected responses are correctly matched to the
              // images upon which the inference was performed
              Some(Responses.featureInferrerDeterministic(id.hashCode))
            } else if (req.contains("palette_inferrer")) {
              Some(Responses.paletteInferrerDeterministic(id.hashCode))
            } else None
        },
      images = _ => Some(Responses.image)
    ) {
      case (QueuePair(queue, dlq), messageSender, augmentedImages, _, _) =>
        images.values.foreach(image =>
          sendNotificationToSQS(queue = queue, body = image.id))
        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          forAll(messageSender.messages.map(_.body)) { id =>
            val image = augmentedImages(id)
            inside(image.state) {
              case ImageState.Augmented(_, id, inferredData) =>
                images should contain key id
                val seed = id.hashCode
                inside(inferredData.value) {
                  case InferredData(
                      features1,
                      features2,
                      lshEncodedFeatures,
                      palette,
                      binSizes,
                      binMinima) =>
                    val featureVector =
                      Responses.randomFeatureVector(seed)
                    features1 should be(featureVector.slice(0, 2048))
                    features2 should be(featureVector.slice(2048, 4096))
                    lshEncodedFeatures should be(
                      Responses.randomLshVector(seed))
                    palette should be(Responses.randomPaletteVector(seed))
                    binSizes should be(Responses.randomBinSizes(seed))
                    binMinima should be(Responses.randomBinMinima(seed))
                }
            }
          }
        }
    }
  }

  it("correctly handles messages that are received more than once") {
    val image = createImageData.toInitialImage
    withResponsesAndFixtures(
      List(image),
      inferrer = req =>
        if (req.contains("feature_inferrer")) {
          Some(Responses.featureInferrer)
        } else if (req.contains("palette_inferrer")) {
          Some(Responses.paletteInferrer)
        } else None,
      images = _ => Some(Responses.image)
    ) {
      case (QueuePair(queue, dlq), messageSender, augmentedImages, _, _) =>
        (1 to 3).foreach(_ =>
          sendNotificationToSQS(queue = queue, body = image.id))
        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          forAll(messageSender.messages.map(_.body)) { id =>
            val image = augmentedImages(id)
            inside(image.state) {
              case ImageState.Augmented(_, _, inferredData) =>
                inside(inferredData.value) {
                  case InferredData(
                      features1,
                      features2,
                      lshEncodedFeatures,
                      palette,
                      binSizes,
                      binMinima) =>
                    features1 should have length 2048
                    features2 should have length 2048
                    every(lshEncodedFeatures) should fullyMatch regex """(\d+)-(\d+)"""
                    every(palette) should fullyMatch regex """\d+"""
                    every(binSizes) should not be empty
                    binMinima should not be empty
                }
            }
          }
        }
    }
  }

  it("places images that fail inference on the DLQ") {
    val image404 = createImageDataWith(
      locations = List(createDigitalLocationWith(url = "lost_image"))
    ).toInitialImage
    val image400 = createImageDataWith(
      locations = List(createDigitalLocationWith(url = "malformed_image_url"))
    ).toInitialImage
    val image500 = createImageDataWith(
      locations =
        List(createDigitalLocationWith(url = "extremely_cursed_image"))
    ).toInitialImage
    withResponsesAndFixtures(
      List(image404, image400, image500),
      inferrer = url =>
        if (url.contains(image400.id)) {
          Some(Responses.badRequest)
        } else if (url.contains(image500.id)) {
          Some(Responses.serverError)
        } else None,
      images = _ => Some(Responses.image)
    ) {
      case (QueuePair(queue, dlq), _, _, _, _) =>
        sendNotificationToSQS(queue = queue, body = image404.id)
        sendNotificationToSQS(queue = queue, body = image400.id)
        sendNotificationToSQS(queue = queue, body = image500.id)
        eventually {
          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, 3)
        }
    }
  }

  it("places images that cannot be downloaded on the DLQ") {
    withResponsesAndFixtures(
      Nil,
      inferrer = _ => Some(Responses.featureInferrer),
      images = _ => None
    ) {
      case (QueuePair(queue, dlq), _, _, _, _) =>
        val image = createImageData.toInitialImage
        sendNotificationToSQS(queue = queue, body = image.id)
        eventually {
          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, 1)
        }
    }
  }

  def withResponsesAndFixtures[R](initialImages: List[Image[Initial]],
                                  inferrer: String => Option[HttpResponse],
                                  images: String => Option[HttpResponse])(
    testWith: TestWith[
      (QueuePair,
       MemoryMessageSender,
       mutable.Map[String, Image[Augmented]],
       RequestPoolMock[(DownloadedImage, InferrerAdapter), Message],
       RequestPoolMock[(Uri, MergedIdentifiedImage), Message]),
      R]): R =
    withResponses(inferrer, images) {
      case (inferrerMock, imagesMock) =>
        val augmentedImages = mutable.Map.empty[String, Image[Augmented]]
        withWorkerServiceFixtures(
          initialImages,
          inferrerMock.pool,
          imagesMock.pool,
          augmentedImages) {
          case (queuePair, sender) =>
            testWith(
              (queuePair, sender, augmentedImages, inferrerMock, imagesMock))
        }
    }

  def withResponses[R](inferrer: String => Option[HttpResponse],
                       images: String => Option[HttpResponse])(
    testWith: TestWith[
      (RequestPoolMock[(DownloadedImage, InferrerAdapter), Message],
       RequestPoolMock[(Uri, MergedIdentifiedImage), Message]),
      R]): R =
    withRequestPool[(DownloadedImage, InferrerAdapter), Message, R](inferrer) {
      inferrerPoolMock =>
        withRequestPool[(Uri, MergedIdentifiedImage), Message, R](images) {
          imagesPoolMock =>
            testWith((inferrerPoolMock, imagesPoolMock))
        }
    }

  def withWorkerServiceFixtures[R](
    initialImages: List[Image[Initial]],
    inferrerRequestPool: RequestPoolFlow[(DownloadedImage, InferrerAdapter),
                                         Message],
    imageRequestPool: RequestPoolFlow[(Uri, MergedIdentifiedImage), Message],
    augmentedImages: mutable.Map[String, Image[Augmented]])(
    testWith: TestWith[(QueuePair, MemoryMessageSender), R]): R =
    withLocalSqsQueuePair() { queuePair =>
      val msgSender = new MemoryMessageSender()
      val fileWriter = new MemoryFileWriter()

      withWorkerService(
        queue = queuePair.queue,
        msgSender = msgSender,
        adapters = Set(
          new FeatureVectorInferrerAdapter("feature_inferrer", 80),
          new PaletteInferrerAdapter("palette_inferrer", 80),
        ),
        fileWriter = fileWriter,
        inferrerRequestPool = inferrerRequestPool,
        imageRequestPool = imageRequestPool,
        initialImages = initialImages,
        augmentedImages = augmentedImages
      ) { _ =>
        testWith((queuePair, msgSender))
      }
    }
}
