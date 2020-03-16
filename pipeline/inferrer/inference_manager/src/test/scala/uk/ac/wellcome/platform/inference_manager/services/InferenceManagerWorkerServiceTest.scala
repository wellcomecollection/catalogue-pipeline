package uk.ac.wellcome.platform.inference_manager.services

import org.scalatest.{FunSpec, Inside, Inspectors, Matchers, OptionValues}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SNS.Topic
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.models.work.internal.{
  AugmentedImage,
  InferredData,
  MergedImage,
  Minted
}
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.ImageGenerators
import uk.ac.wellcome.platform.inference_manager.fixtures.{
  FeatureVectorInferrerMock,
  InferenceManagerWorkerServiceFixture
}

class InferenceManagerWorkerServiceTest
    extends FunSpec
    with Matchers
    with ImageGenerators
    with Inside
    with OptionValues
    with Inspectors
    with InferenceManagerWorkerServiceFixture[
      MergedImage[Minted],
      AugmentedImage[Minted]
    ] {

  it(
    "reads image messages, augments them with the inferrer, and sends them to SNS") {
    withWorkerServiceFixtures {
      case (QueuePair(queue, dlq), topic) =>
        val image = createMergedImage.toMinted
        sendMessage(queue, image)
        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
          val augmentedWork = getMessages[AugmentedImage[Minted]](topic).head
          inside(augmentedWork) {
            case AugmentedImage(id, _, _, _, inferredData) =>
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

  def withWorkerServiceFixtures[R](
    testWith: TestWith[(QueuePair, Topic), R]): R =
    withLocalSqsQueueAndDlq { queuePair =>
      withLocalSnsTopic { topic =>
        withWorkerService(
          queuePair.queue,
          topic,
          FeatureVectorInferrerAdapter,
          FeatureVectorInferrerMock) { _ =>
          testWith((queuePair, topic))
        }
      }
    }
}
