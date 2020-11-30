package uk.ac.wellcome.platform.transformer.miro

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
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
import org.scalatest.Assertion
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.platform.transformer.miro.models.MiroVHSRecord
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.storage.generators.S3ObjectLocationGenerators
import uk.ac.wellcome.storage.s3.S3ObjectLocation
import uk.ac.wellcome.storage.store.Readable
import uk.ac.wellcome.storage.store.memory.MemoryStore

class MiroTransformerFeatureTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with MiroRecordGenerators
    with MiroTransformableWrapper
    with Akka
    with SQS
    with S3ObjectLocationGenerators {

  it("transforms miro records and sends on the transformed result") {
    val miroID = "M0000001"
    val title = "A guide for a giraffe"

    val record = createMiroRecordWith(title = Some(title), imageNumber = miroID)

    val vhsStore =
      new MemoryStore[String, MiroVHSRecord](initialEntries = Map.empty)
    val typedStore =
      new MemoryStore[S3ObjectLocation, MiroRecord](initialEntries = Map.empty)

    storeRecord(
      id = miroID,
      record = record,
      vhsStore = vhsStore,
      typedStore = typedStore)

    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue(visibilityTimeout = 5) { queue =>
      sendNotificationToSQS(queue, Version(miroID, version = 1))

      withWorkerService(messageSender, queue, vhsStore, typedStore) { _ =>
        eventually {
          val works = messageSender.getMessages[Work.Visible[Source]]
          works.length shouldBe 1

          val actualWork = works.head
          actualWork.identifiers.head.value shouldBe miroID
          actualWork.data.title shouldBe Some(title)
        }
      }
    }
  }

  private def storeRecord(
    id: String,
    record: MiroRecord,
    vhsStore: MemoryStore[String, MiroVHSRecord],
    typedStore: MemoryStore[S3ObjectLocation, MiroRecord]
  ): Assertion = {
    val s3Location = createS3ObjectLocation
    typedStore.put(s3Location)(record) shouldBe a[Right[_, _]]

    val vhsRecord = MiroVHSRecord(
      id = id,
      version = 1,
      isClearedForCatalogueAPI = true,
      location = s3Location
    )
    vhsStore.put(id)(vhsRecord) shouldBe a[Right[_, _]]
  }

  def withWorkerService[R](
    messageSender: MemoryMessageSender,
    queue: Queue,
    miroVhsReader: Readable[String, MiroVHSRecord] =
      new MemoryStore[String, MiroVHSRecord](initialEntries = Map.empty),
    typedStore: Readable[S3ObjectLocation, MiroRecord] =
      new MemoryStore[S3ObjectLocation, MiroRecord](initialEntries = Map.empty)
  )(testWith: TestWith[MiroTransformerWorkerService[String], R]): R =
    withActorSystem { implicit actorSystem =>
      withSQSStream[NotificationMessage, R](queue) { sqsStream =>
        val workerService = new MiroTransformerWorkerService(
          stream = sqsStream,
          sender = messageSender,
          miroVhsReader = miroVhsReader,
          typedStore = typedStore
        )

        workerService.run()

        testWith(workerService)
      }
    }
}
