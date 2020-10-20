package uk.ac.wellcome.platform.id_minter_images

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import software.amazon.awssdk.services.sqs.model.QueueAttributeName
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.work.generators.ImageGenerators
import uk.ac.wellcome.platform.id_minter_images.fixtures.WorkerServiceFixture
import uk.ac.wellcome.models.Implicits._
import SourceWork._

class IdMinterFeatureTest
    extends AnyFunSpec
    with Matchers
    with IntegrationPatience
    with Eventually
    with WorkerServiceFixture
    with ImageGenerators {

  it("mints the same IDs where source identifiers match") {
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue() { queue =>
      withIdentifiersTable { identifiersTableConfig =>
        withWorkerService(messageSender, queue, identifiersTableConfig) { _ =>
          eventuallyTableExists(identifiersTableConfig)
          val image = createUnmergedImage mergeWith (
            mergedWork().toSourceWork, None, 1
          )

          val messageCount = 5

          (1 to messageCount).foreach { _ =>
            sendMessage(queue = queue, obj = image)
          }

          eventually {
            val images =
              messageSender.getMessages[MergedImage[DataState.Identified]]
            images.length shouldBe >=(messageCount)

            images.map(_.id.canonicalId).distinct should have size 1
            images.foreach { receivedImage =>
              receivedImage.id.sourceIdentifier shouldBe image.id.sourceIdentifier
              receivedImage.location shouldBe image.location
            }
          }
        }
      }
    }
  }

  it("continues if something fails processing a message") {
    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue() { queue =>
      withIdentifiersTable { identifiersTableConfig =>
        withWorkerService(messageSender, queue, identifiersTableConfig) { _ =>
          sendInvalidJSONto(queue)

          val image = createUnmergedImage mergeWith (
            mergedWork().toSourceWork, None, 1
          )

          sendMessage(queue = queue, obj = image)

          eventually {
            messageSender.messages should not be empty

            assertMessageIsNotDeleted(queue)
          }
        }
      }
    }
  }

  private def assertMessageIsNotDeleted(queue: Queue): Unit = {
    // After a message is read, it stays invisible for 1 second and then it gets sent again.
    // So we wait for longer than the visibility timeout and then we assert that it has become
    // invisible again, which means that the id_minter picked it up again,
    // and so it wasn't deleted as part of the first run.
    // TODO Write this test using dead letter queues once https://github.com/adamw/elasticmq/issues/69 is closed
    Thread.sleep(2000)

    getQueueAttribute(
      queue,
      attributeName =
        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE) shouldBe "1"
  }
}
