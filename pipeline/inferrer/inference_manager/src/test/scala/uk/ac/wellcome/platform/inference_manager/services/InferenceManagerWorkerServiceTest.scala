package uk.ac.wellcome.platform.inference_manager.services

import akka.http.scaladsl.model.HttpResponse
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.{BeforeAndAfterAll, Inside, Inspectors, OptionValues}
import software.amazon.awssdk.services.sqs.model.Message
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.work.internal.{AugmentedImage, InferredData}
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.ImageGenerators
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
      .map(_ => createIdentifiedMergedImageWith())
      .map(image => image.id -> image)
      .toMap
    withResponsesAndFixtures(
      inferrer = req =>
        images.keys
          .map(_.canonicalId)
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
      case (QueuePair(queue, dlq), messageSender, _, _) =>
        images.values.foreach(image => sendMessage(queue, image))
        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          forAll(messageSender.getMessages[AugmentedImage]) { image =>
            inside(image) {
              case AugmentedImage(id, _, _, _, _, inferredData) =>
                images should contain key id
                val seed = id.canonicalId.hashCode
                inside(inferredData.value) {
                  case InferredData(
                      features1,
                      features2,
                      lshEncodedFeatures,
                      palette) =>
                    val featureVector =
                      Responses.randomFeatureVector(seed)
                    features1 should be(featureVector.slice(0, 2048))
                    features2 should be(featureVector.slice(2048, 4096))
                    lshEncodedFeatures should be(
                      Responses.randomLshVector(seed))
                    palette should be(Responses.randomPaletteVector(seed))
                }
            }
          }
        }
    }
  }

  it("correctly handles messages that are received more than once") {
    withResponsesAndFixtures(
      inferrer = req =>
        if (req.contains("feature_inferrer")) {
          Some(Responses.featureInferrer)
        } else if (req.contains("palette_inferrer")) {
          Some(Responses.paletteInferrer)
        } else None,
      images = _ => Some(Responses.image)
    ) {
      case (QueuePair(queue, dlq), messageSender, _, _) =>
        val image = createIdentifiedMergedImageWith()
        (1 to 3).foreach(_ => sendMessage(queue, image))
        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)

          forAll(messageSender.getMessages[AugmentedImage]) { image =>
            inside(image) {
              case AugmentedImage(_, _, _, _, _, inferredData) =>
                inside(inferredData.value) {
                  case InferredData(
                      features1,
                      features2,
                      lshEncodedFeatures,
                      palette) =>
                    features1 should have length 2048
                    features2 should have length 2048
                    every(lshEncodedFeatures) should fullyMatch regex """(\d+)-(\d+)"""
                    every(palette) should fullyMatch regex """\d+"""
                }
            }
          }
        }
    }
  }

  it("places images that fail inference on the DLQ") {
    val image404 = createIdentifiedMergedImageWith(
      location = createDigitalLocationWith(url = "lost_image")
    )
    val image400 = createIdentifiedMergedImageWith(
      location = createDigitalLocationWith(url = "malformed_image_url")
    )
    val image500 = createIdentifiedMergedImageWith(
      location = createDigitalLocationWith(url = "extremely_cursed_image")
    )
    withResponsesAndFixtures(
      inferrer = url =>
        if (url.contains(image400.id.canonicalId)) {
          Some(Responses.badRequest)
        } else if (url.contains(image500.id.canonicalId)) {
          Some(Responses.serverError)
        } else None,
      images = _ => Some(Responses.image)
    ) {
      case (QueuePair(queue, dlq), _, _, _) =>
        sendMessage(queue, image404)
        sendMessage(queue, image400)
        sendMessage(queue, image500)
        eventually {
          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, 3)
        }
    }
  }

  it("places images that cannot be downloaded on the DLQ") {
    withResponsesAndFixtures(
      inferrer = _ => Some(Responses.featureInferrer),
      images = _ => None
    ) {
      case (QueuePair(queue, dlq), _, _, _) =>
        val image = createIdentifiedMergedImageWith()
        sendMessage(queue, image)
        eventually {
          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, 1)
        }
    }
  }

  def withResponsesAndFixtures[R](inferrer: String => Option[HttpResponse],
                                  images: String => Option[HttpResponse])(
    testWith: TestWith[
      (QueuePair,
       MemoryMessageSender,
       RequestPoolMock[(DownloadedImage, InferrerAdapter), Message],
       RequestPoolMock[MergedIdentifiedImage, Message]),
      R]): R =
    withResponses(inferrer, images) {
      case (inferrerMock, imagesMock) =>
        withWorkerServiceFixtures(inferrerMock.pool, imagesMock.pool) {
          case (queuePair, sender) =>
            testWith((queuePair, sender, inferrerMock, imagesMock))
        }
    }

  def withResponses[R](inferrer: String => Option[HttpResponse],
                       images: String => Option[HttpResponse])(
    testWith: TestWith[
      (RequestPoolMock[(DownloadedImage, InferrerAdapter), Message],
       RequestPoolMock[MergedIdentifiedImage, Message]),
      R]): R =
    withRequestPool[(DownloadedImage, InferrerAdapter), Message, R](inferrer) {
      inferrerPoolMock =>
        withRequestPool[MergedIdentifiedImage, Message, R](images) {
          imagesPoolMock =>
            testWith((inferrerPoolMock, imagesPoolMock))
        }
    }

  def withWorkerServiceFixtures[R](
    inferrerRequestPool: RequestPoolFlow[(DownloadedImage, InferrerAdapter),
                                         Message],
    imageRequestPool: RequestPoolFlow[MergedIdentifiedImage, Message])(
    testWith: TestWith[(QueuePair, MemoryMessageSender), R]): R =
    withLocalSqsQueuePair() { queuePair =>
      val messageSender = new MemoryMessageSender()
      val fileWriter = new MemoryFileWriter()

      withWorkerService(
        queue = queuePair.queue,
        messageSender = messageSender,
        adapters = Set(
          new FeatureVectorInferrerAdapter("feature_inferrer", 80),
          new PaletteInferrerAdapter("palette_inferrer", 80),
        ),
        fileWriter = fileWriter,
        inferrerRequestPool = inferrerRequestPool,
        imageRequestPool = imageRequestPool
      ) { _ =>
        testWith((queuePair, messageSender))
      }
    }
}
