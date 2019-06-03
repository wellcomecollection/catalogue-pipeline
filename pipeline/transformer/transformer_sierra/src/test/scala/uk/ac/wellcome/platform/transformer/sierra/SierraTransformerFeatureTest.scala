package uk.ac.wellcome.platform.transformer.sierra

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.transformable.SierraTransformable
import uk.ac.wellcome.models.transformable.SierraTransformable._
import uk.ac.wellcome.models.transformable.sierra.test.utils.SierraGenerators
import uk.ac.wellcome.models.work.internal.UnidentifiedWork
import uk.ac.wellcome.platform.transformer.sierra.fixtures.RecordReceiverFixture
import uk.ac.wellcome.platform.transformer.sierra.services.SierraTransformerWorkerService
import uk.ac.wellcome.storage.streaming.CodecInstances._

class SierraTransformerFeatureTest
    extends FunSpec
    with Matchers
    with SQS
    with Eventually
    with RecordReceiverFixture
    with IntegrationPatience
    with SierraGenerators {

  it("transforms sierra records and publishes the result") {
    val id = createSierraBibNumber
    val title = "A pot of possums"

    val store = new SierraTransformableStore()
    val workSender = new WorkSender()

    withLocalSqsQueue { queue =>
      val data =
        s"""
           |{
           | "id": "$id",
           | "title": "$title",
           | "varFields": []
           |}
                  """.stripMargin

      val sierraTransformable = SierraTransformable(
        bibRecord = createSierraBibRecordWith(
          id = id,
          data = data
        )
      )

      val message = createSierraNotificationMessageWith(store, sierraTransformable)

      sendSqsMessage(queue, message)

      val sourceIdentifier = createSierraSystemSourceIdentifierWith(
        value = id.withCheckDigit
      )

      val sierraIdentifier =
        createSierraIdentifierSourceIdentifierWith(
          value = id.withoutCheckDigit
        )


      withWorkerService(workSender, store, queue) { _ =>
        eventually {
          val works = workSender.getMessages[UnidentifiedWork]
          works.size should be >= 1


          works.map { actualWork =>
            actualWork.sourceIdentifier shouldBe sourceIdentifier
            actualWork.title shouldBe title
            actualWork.identifiers shouldBe List(
              sourceIdentifier,
              sierraIdentifier)
          }
        }
      }
    }
  }

  def withWorkerService[R](messageSender: WorkSender, store: SierraTransformableStore, queue: Queue)(
    testWith: TestWith[SierraTransformerWorkerService[String], R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue) { sqsStream =>
        val messageReceiver = createRecordReceiver(store, messageSender)
        val workerService = new SierraTransformerWorkerService(
          messageReceiver = messageReceiver,
          sierraTransformer = new SierraTransformableTransformer,
          sqsStream = sqsStream
        )

        workerService.run()

        testWith(workerService)
      }
    }
}
