package weco.pipeline.transformer.calm.services

import io.circe.Encoder
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder
import org.scalatest.EitherValues
import weco.catalogue.internal_model.identifiers.IdentifierType
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.catalogue.source_model.CalmSourcePayload
import weco.catalogue.source_model.calm.CalmRecord
import weco.catalogue.source_model.generators.CalmRecordGenerators
import weco.fixtures.TestWith
import weco.json.JsonUtil._
import weco.messaging.fixtures.SQS.QueuePair
import weco.messaging.memory.MemoryMessageSender
import weco.messaging.sns.NotificationMessage
import weco.pipeline.transformer.calm.models.CalmSourceData
import weco.pipeline.transformer.{TransformerWorker, TransformerWorkerTestCases}
import weco.pipeline_storage.memory.MemoryIndexer
import weco.pipeline_storage.{PipelineStorageStream, Retriever}
import weco.storage.generators.S3ObjectLocationGenerators
import weco.storage.s3.S3ObjectLocation
import weco.storage.store.memory.MemoryTypedStore

import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits.global

class CalmTransformerWorkerTest
    extends TransformerWorkerTestCases[
      MemoryTypedStore[S3ObjectLocation, CalmRecord],
      CalmSourcePayload,
      CalmSourceData]
    with EitherValues
    with CalmRecordGenerators
    with S3ObjectLocationGenerators {

  it("creates a deleted work when the CalmSourcePayload has isDeleted = true") {
    withContext { implicit context =>
      val payload = createPayload.copy(isDeleted = true)
      val workIndexer = new MemoryIndexer[Work[Source]]()
      val workKeySender = new MemoryMessageSender()

      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withWorkerImpl(queue, workIndexer, workKeySender) { _ =>
            sendNotificationToSQS(queue, payload)

            eventually {
              assertQueueEmpty(dlq)
              assertQueueEmpty(queue)

              workIndexer.index should have size 1

              val sentKeys = workKeySender.messages.map { _.body }
              val storedKeys = workIndexer.index.keys
              sentKeys should contain theSameElementsAs storedKeys

              workIndexer.index.values.head shouldBe a[Work.Deleted[_]]
            }
          }
      }
    }
  }

  override def withContext[R](
    testWith: TestWith[MemoryTypedStore[S3ObjectLocation, CalmRecord], R]): R =
    testWith(
      MemoryTypedStore[S3ObjectLocation, CalmRecord](initialEntries = Map.empty)
    )

  override def createId: String = UUID.randomUUID().toString

  override def createPayloadWith(id: String, version: Int)(
    implicit store: MemoryTypedStore[S3ObjectLocation, CalmRecord])
    : CalmSourcePayload = {
    val record = createCalmRecordWith(
      "Title" -> "abc",
      "Level" -> "Collection",
      "RefNo" -> "a/b/c",
      "AltRefNo" -> "a.b.c",
      "CatalogueStatus" -> "Catalogued"
    )

    val location = createS3ObjectLocation

    store.put(location)(record.copy(id = id)) shouldBe a[Right[_, _]]

    CalmSourcePayload(id = id, location = location, version = version)
  }

  override def setPayloadVersion(p: CalmSourcePayload, version: Int)(
    implicit store: MemoryTypedStore[S3ObjectLocation, CalmRecord])
    : CalmSourcePayload = {
    val storedData: CalmRecord = store.get(p.location).value.identifiedT

    val location = createS3ObjectLocation
    store.put(location)(storedData) shouldBe a[Right[_, _]]

    p.copy(location = location, version = version)
  }

  override def createBadPayload(
    implicit store: MemoryTypedStore[S3ObjectLocation, CalmRecord])
    : CalmSourcePayload =
    CalmSourcePayload(
      id = UUID.randomUUID().toString,
      location = createS3ObjectLocation,
      version = 1)

  override implicit val encoder: Encoder[CalmSourcePayload] =
    deriveConfiguredEncoder[CalmSourcePayload]

  override def assertMatches(p: CalmSourcePayload, w: Work[WorkState.Source])(
    implicit store: MemoryTypedStore[S3ObjectLocation, CalmRecord]): Unit = {
    w.sourceIdentifier.identifierType shouldBe IdentifierType.CalmRecordIdentifier
    w.sourceIdentifier.value shouldBe p.id
  }

  override def withWorker[R](
    pipelineStream: PipelineStorageStream[NotificationMessage,
                                          Work[WorkState.Source],
                                          String],
    retriever: Retriever[Work[WorkState.Source]])(
    testWith: TestWith[
      TransformerWorker[CalmSourcePayload, CalmSourceData, String],
      R])(
    implicit recordReadable: MemoryTypedStore[S3ObjectLocation, CalmRecord])
    : R =
    testWith(
      new CalmTransformerWorker(
        pipelineStream = pipelineStream,
        recordReadable = recordReadable,
        retriever = retriever
      )
    )
}
