package weco.catalogue.sierra_record_merger

import io.circe.Encoder
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.Queue
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.sierra_adapter.model._
import uk.ac.wellcome.sierra_adapter.model.Implicits._
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers
import uk.ac.wellcome.storage.Version
import weco.catalogue.sierra_merger.models.TransformableOps
import weco.catalogue.sierra_record_merger.services.Worker
import weco.catalogue.source_model.fixtures.SourceVHSFixture
import weco.catalogue.source_model.store.SourceVHS

trait SierraRecordMergerFeatureTest[Record <: AbstractSierraRecord[_]]
    extends AnyFunSpec
    with SQS
    with SourceVHSFixture
    with SierraGenerators
    with Eventually
    with IntegrationPatience
    with SierraAdapterHelpers {

  def withWorker[R](queue: Queue,
                    sourceVHS: SourceVHS[SierraTransformable] =
                      createSourceVHS[SierraTransformable])(
    testWith: TestWith[(Worker[SierraItemRecord, String], MemoryMessageSender),
                       R]): R

  def createRecordWith(bibIds: List[SierraBibNumber]): Record

  implicit val encoder: Encoder[Record]
  implicit val transformableOps: TransformableOps[Record]

  it("stores a record from SQS") {
    withLocalSqsQueue() { queue =>
      val bibId = createSierraBibNumber
      val record = createRecordWith(bibIds = List(bibId))

      val sourceVHS = createSourceVHS[SierraTransformable]

      withWorker(queue, sourceVHS) {
        case (_, messageSender) =>
          sendNotificationToSQS(queue = queue, record)

          val expectedSierraTransformable =
            transformableOps.create(bibId, record)

          eventually {
            assertStoredAndSent(
              Version(
                expectedSierraTransformable.sierraId.withoutCheckDigit,
                0),
              expectedSierraTransformable,
              sourceVHS,
              messageSender
            )
          }
      }
    }
  }

  it("stores multiple items from SQS") {
    withLocalSqsQueue() { queue =>
      val bibId1 = createSierraBibNumber
      val record1 = createRecordWith(bibIds = List(bibId1))

      val bibId2 = createSierraBibNumber
      val record2 = createRecordWith(bibIds = List(bibId2))

      val sourceVHS = createSourceVHS[SierraTransformable]

      withWorker(queue, sourceVHS) {
        case (_, messageSender) =>
          sendNotificationToSQS(queue, record1)
          sendNotificationToSQS(queue, record2)

          eventually {
            val expectedSierraTransformable1 =
              transformableOps.create(bibId1, record1)

            val expectedSierraTransformable2 =
              transformableOps.create(bibId2, record2)

            assertStoredAndSent(
              Version(bibId1.withoutCheckDigit, 0),
              expectedSierraTransformable1,
              sourceVHS,
              messageSender
            )
            assertStoredAndSent(
              Version(bibId2.withoutCheckDigit, 0),
              expectedSierraTransformable2,
              sourceVHS,
              messageSender
            )
          }
      }
    }
  }

  it("sends a notification for every transformable which changes") {
    withLocalSqsQueue() { queue =>
      val bibIds = createSierraBibNumbers(3)
      val record = createRecordWith(bibIds = bibIds)

      val sourceVHS = createSourceVHS[SierraTransformable]

      withWorker(queue, sourceVHS) {
        case (_, messageSender) =>
          sendNotificationToSQS(queue = queue, record)

          val expectedTransformables = bibIds.map { bibId =>
            transformableOps.create(bibId, record)
          }

          eventually {
            expectedTransformables.map { tranformable =>
              assertStoredAndSent(
                Version(tranformable.sierraId.withoutCheckDigit, 0),
                tranformable,
                sourceVHS,
                messageSender
              )
            }
          }
      }
    }
  }
}
