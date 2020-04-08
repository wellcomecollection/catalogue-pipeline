package uk.ac.wellcome.platform.inference_manager.services

import org.scalatest.{BeforeAndAfterAll, FunSpec, Inside, Inspectors, Matchers, OptionValues}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.models.work.internal.{AugmentedImage, Identified, InferredData, MergedImage}
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.ImageGenerators
import uk.ac.wellcome.platform.inference_manager.fixtures.{FeatureVectorInferrerMock, InferenceManagerWorkerServiceFixture, InferrerWiremock}

class InferenceManagerWorkerServiceTest
    extends FunSpec
    with Matchers
    with ImageGenerators
    with Inside
    with OptionValues
    with Inspectors
    with BeforeAndAfterAll
    with InferenceManagerWorkerServiceFixture[
      MergedImage[Identified],
      AugmentedImage[Identified]
    ] {

  val inferrerMock = new InferrerWiremock(FeatureVectorInferrerMock)

  override def beforeAll(): Unit = {
    inferrerMock.start()
    super.beforeAll()
  }

  override def afterAll(): Unit = {
    inferrerMock.stop()
    super.afterAll()
  }

  it(
    "reads image messages, augments them with the inferrer, and sends them to SNS") {
    withWorkerServiceFixtures {
      case (QueuePair(queue, dlq), topic) =>
        val image = createMergedImage.toMinted
        sendMessage(queue, image)
        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
          val augmentedWork = getMessages[AugmentedImage[Identified]](topic).head
          inside(augmentedWork) {
            case AugmentedImage(id, _, _, _, _, inferredData) =>
              id should be(image.id)
              inside(inferredData.value) {
                case InferredData(features1, features2, lshEncodedFeatures) =>
                  features1 should have length 2048
                  features2 should have length 2048
                  every(lshEncodedFeatures) should fullyMatch regex """(\d+)-(\d+)"""
              }
          }
        }
    }
  }

  it("places images that fail inference deterministically on the DLQ") {
    withWorkerServiceFixtures {
      case (QueuePair(queue, dlq), _) =>
        val image404 = createMergedImageWith(
          location = createDigitalLocationWith(url = "lost_image")
        ).toMinted
        val image400 = createMergedImageWith(
          location = createDigitalLocationWith(url = "malformed_image_url")
        ).toMinted
        sendMessage(queue, image404)
        sendMessage(queue, image400)
        eventually {
          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, 2)
        }
    }
  }

  it("allows images that fail inference nondeterministically to pass through") {
    withWorkerServiceFixtures {
      case (QueuePair(queue, dlq), topic) =>
        val image500 = createMergedImageWith(
          location = createDigitalLocationWith(url = "extremely_cursed_image")
        ).toMinted
        sendMessage(queue, image500)
        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
          val output = getMessages[AugmentedImage[Identified]](topic).head
          inside(output) {
            case AugmentedImage(id, _, _, _, _, inferredData) =>
              id should be(image500.id)
              inferredData should not be defined
          }
        }
    }
  }

  def withWorkerServiceFixtures[R](
    testWith: TestWith[(QueuePair, Topic), R]): R =
    withLocalSqsQueueAndDlq { queuePair =>
      withLocalSnsTopic { topic =>
        withWorkerService(
          queuePair.queue,
          topic,
          FeatureVectorInferrerAdapter,
          inferrerMock.port) { _ =>
          testWith((queuePair, topic))
        }
      }
    }
}
