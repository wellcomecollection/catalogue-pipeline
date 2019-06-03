package uk.ac.wellcome.platform.transformer.miro

import org.scalatest.concurrent.Eventually
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.internal.UnidentifiedWork
import uk.ac.wellcome.platform.transformer.miro.fixtures.MiroVHSRecordReceiverFixture
import uk.ac.wellcome.platform.transformer.miro.generators.MiroRecordGenerators
import uk.ac.wellcome.platform.transformer.miro.services.MiroTransformerWorkerService
import uk.ac.wellcome.platform.transformer.miro.transformers.MiroTransformableWrapper
import uk.ac.wellcome.storage.streaming.CodecInstances._

class MiroTransformerFeatureTest
    extends FunSpec
    with Matchers
    with Eventually
    with MiroRecordGenerators
    with MiroTransformableWrapper
    with MiroVHSRecordReceiverFixture {

  it("transforms miro records and publishes the result to the given topic") {
    val miroID = "M0000001"
    val title = "A guide for a giraffe"

    val workSender = new WorkSender()
    val store = new MiroRecordStore()

    withLocalSqsQueue { queue =>
      val miroHybridRecordMessage =
        createMiroVHSRecordNotificationMessageWith(
          store,
          miroRecord = createMiroRecordWith(
            title = Some(title),
            imageNumber = miroID
          )
        )

      sendSqsMessage(
        queue = queue,
        obj = miroHybridRecordMessage
      )

      withWorkerService(workSender, store, queue) { _ =>
        eventually {
          val works = workSender.getMessages[UnidentifiedWork]
          works.length shouldBe >=(1)

          works.map { actualWork =>
            actualWork.identifiers.head.value shouldBe miroID
            actualWork.title shouldBe title
          }
        }
      }
    }
  }

  // This is based on a specific bug that we found where different records
  // were written to the same s3 key because of the hashing algorithm clashing
  it("sends different messages for different miro records") {
    val workSender = new WorkSender()
    val store = new MiroRecordStore()

    withLocalSqsQueue { queue =>
      val miroHybridRecordMessage1 =
        createMiroVHSRecordNotificationMessageWith(
          store,
          miroRecord = createMiroRecordWith(
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
        createMiroVHSRecordNotificationMessageWith(
          store,
          miroRecord = createMiroRecordWith(
            title =
              Some("Greenfield Sluder, Tonsillectomy..., use of guillotine"),
            description = Some("Use of the guillotine"),
            copyrightCleared = Some("Y"),
            imageNumber = "L0023034",
            useRestrictions = Some("CC-BY"),
            innopacID = Some("12074536"),
            creditLine = Some("Wellcome Library, London")
          )
        )

      withWorkerService(workSender, store, queue) { _ =>
        sendSqsMessage(queue = queue, obj = miroHybridRecordMessage1)
        sendSqsMessage(queue = queue, obj = miroHybridRecordMessage2)

        eventually {
          val works = workSender.getMessages[UnidentifiedWork]
          works.distinct.length shouldBe 2
        }
      }
    }
  }

  def withWorkerService[R](workSender: WorkSender,
                           miroRecordStore: MiroRecordStore,
                           queue: Queue)(
    testWith: TestWith[MiroTransformerWorkerService[String], R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue) { sqsStream =>
        val workerService = new MiroTransformerWorkerService(
          vhsRecordReceiver = createRecordReceiver(miroRecordStore, workSender),
          miroTransformer = new MiroRecordTransformer,
          sqsStream = sqsStream
        )

        workerService.run()

        testWith(workerService)
      }
    }
}
