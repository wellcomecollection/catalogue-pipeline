package uk.ac.wellcome.platform.transformer.miro

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.platform.transformer.miro.fixtures.MiroVHSRecordReceiverFixture
import uk.ac.wellcome.platform.transformer.miro.generators.MiroRecordGenerators
import uk.ac.wellcome.platform.transformer.miro.transformers.MiroTransformableWrapper
import uk.ac.wellcome.platform.transformer.miro.services.MiroTransformerWorkerService
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import WorkState.Source

class MiroTransformerFeatureTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with MiroRecordGenerators
    with MiroTransformableWrapper
    with MiroVHSRecordReceiverFixture
    with Akka {

  it("transforms miro records and publishes the result to the given topic") {
    val miroID = "M0000001"
    val title = "A guide for a giraffe"

    val miroHybridRecordMessage =
      createHybridRecordNotificationWith(
        createMiroRecordWith(title = Some(title), imageNumber = miroID)
      )

    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue() { queue =>
      sendSqsMessage(queue, miroHybridRecordMessage)

      withWorkerService(messageSender, queue) { _ =>
        eventually {
          val works = messageSender.getMessages[Work.Visible[Source]]
          works.length shouldBe >=(1)

          works.map { actualWork =>
            actualWork.identifiers.head.value shouldBe miroID
            actualWork.data.title shouldBe Some(title)
          }
        }
      }
    }
  }

  // This is based on a specific bug that we found where different records
  // were written to the same s3 key because of the hashing algorithm clashing
  it("sends different messages for different miro records") {
    val miroHybridRecordMessage1 =
      createHybridRecordNotificationWith(
        createMiroRecordWith(
          title = Some("Antonio Dionisi"),
          description = Some("Antonio Dionisi"),
          physFormat = Some("Book"),
          copyrightCleared = Some("Y"),
          imageNumber = "L0011975",
          useRestrictions = Some("CC-BY"),
          innopacID = Some("12917175"),
          creditLine = Some("Wellcome Library, London")
        )
      )

    val miroHybridRecordMessage2 =
      createHybridRecordNotificationWith(
        createMiroRecordWith(
          title = Some("Greenfield Sluder, Tonsillectomy..., use of guillotine"),
          description = Some("Use of the guillotine"),
          copyrightCleared = Some("Y"),
          imageNumber = "L0023034",
          useRestrictions = Some("CC-BY"),
          innopacID = Some("12074536"),
          creditLine = Some("Wellcome Library, London")
        )
      )

    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue() { queue =>
      withWorkerService(messageSender, queue) { _ =>
        sendSqsMessage(queue = queue, obj = miroHybridRecordMessage1)
        sendSqsMessage(queue = queue, obj = miroHybridRecordMessage2)

        eventually {
          messageSender
            .getMessages[Work.Visible[Source]]
            .distinct should have size 2
        }
      }
    }
  }

  def withWorkerService[R](messageSender: MemoryMessageSender, queue: Queue)(
    testWith: TestWith[MiroTransformerWorkerService[String], R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue) { sqsStream =>
        val workerService = new MiroTransformerWorkerService(
          vhsRecordReceiver = createRecordReceiverWith(messageSender),
          miroTransformer = new MiroRecordTransformer,
          sqsStream = sqsStream
        )

        workerService.run()

        testWith(workerService)
      }
    }
}
