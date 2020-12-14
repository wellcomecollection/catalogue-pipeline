package uk.ac.wellcome.platform.sierra_bib_merger

import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.platform.sierra_bib_merger.fixtures.WorkerServiceFixture
import uk.ac.wellcome.sierra_adapter.model.{
  SierraGenerators,
  SierraTransformable
}
import uk.ac.wellcome.sierra_adapter.model.Implicits._
import uk.ac.wellcome.storage.streaming.Codec._
import uk.ac.wellcome.storage.Version
import weco.catalogue.source_model.SierraSourcePayload

class SierraBibMergerFeatureTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with ScalaFutures
    with SierraGenerators
    with WorkerServiceFixture {

  it("stores a bib in the hybrid store") {
    val sourceVHS = createSourceVHS[SierraTransformable]

    withLocalSqsQueue() { queue =>
      withWorkerService(sourceVHS, queue) {
        case (_, messageSender) =>
          val bibRecord = createSierraBibRecord

          sendNotificationToSQS(queue = queue, message = bibRecord)

          val expectedSierraTransformable =
            SierraTransformable(bibRecord = bibRecord)

          val id =
            Version(
              id = expectedSierraTransformable.sierraId.withoutCheckDigit,
              version = 0
            )

          eventually {
            assertStoredAndSent(
              id,
              expectedSierraTransformable,
              sourceVHS,
              messageSender
            )

            messageSender
              .getMessages[SierraSourcePayload]
              .map { p => Version(p.id, p.version) } shouldBe Seq(id)
          }
      }
    }
  }

  it("stores multiple bibs from SQS") {
    val sourceVHS = createSourceVHS[SierraTransformable]

    withLocalSqsQueue() { queue =>
      withWorkerService(sourceVHS, queue) {
        case (_, messageSender) =>
          val record1 = createSierraBibRecord
          sendNotificationToSQS(queue = queue, message = record1)

          val expectedTransformable1 =
            SierraTransformable(bibRecord = record1)

          val record2 = createSierraBibRecord

          sendNotificationToSQS(queue = queue, message = record2)

          val expectedTransformable2 =
            SierraTransformable(bibRecord = record2)

          val id1 = Version(expectedTransformable1.sierraId.withoutCheckDigit, 0)
          val id2 = Version(expectedTransformable2.sierraId.withoutCheckDigit, 0)

          eventually {
            assertStoredAndSent(
              id1,
              expectedTransformable1,
              sourceVHS,
              messageSender
            )
            assertStoredAndSent(
              id2,
              expectedTransformable2,
              sourceVHS,
              messageSender
            )
          }

          messageSender
            .getMessages[SierraSourcePayload]
            .map { p => Version(p.id, p.version) } shouldBe Seq(id1, id2)
      }
    }
  }

  it("updates a bib if a newer version is sent to SQS") {
    val oldBibRecord = createSierraBibRecordWith(
      modifiedDate = olderDate
    )

    val oldTransformable =
      SierraTransformable(bibRecord = oldBibRecord)

    val sourceVHS = createSourceVHSWith(
      initialEntries = Map(
        Version(oldTransformable.sierraId.withoutCheckDigit, 0) -> oldTransformable
      )
    )

    withLocalSqsQueue() { queue =>
      withWorkerService(sourceVHS, queue) {
        case (_, messageSender) =>
          val newBibRecord = createSierraBibRecordWith(
            id = oldBibRecord.id,
            modifiedDate = newerDate
          )

          sendNotificationToSQS(queue = queue, message = newBibRecord)

          val expectedTransformable =
            SierraTransformable(bibRecord = newBibRecord)

          val id = Version(oldTransformable.sierraId.withoutCheckDigit, 1)

          eventually {
            assertStoredAndSent(
              id,
              expectedTransformable,
              sourceVHS,
              messageSender
            )
          }

          messageSender
            .getMessages[SierraSourcePayload]
            .map { p => Version(p.id, p.version) } shouldBe Seq(id)
      }
    }
  }

  it("only applies an update once, even if it's sent multiple times") {
    val oldBibRecord = createSierraBibRecordWith(
      modifiedDate = olderDate
    )

    val oldTransformable =
      SierraTransformable(bibRecord = oldBibRecord)

    val sourceVHS = createSourceVHSWith(
      initialEntries = Map(
        Version(oldTransformable.sierraId.withoutCheckDigit, 0) -> oldTransformable
      )
    )

    withLocalSqsQueue() { queue =>
      withWorkerService(sourceVHS, queue) {
        case (_, messageSender) =>
          val newBibRecord = createSierraBibRecordWith(
            id = oldBibRecord.id,
            modifiedDate = newerDate
          )

          (1 to 5).map { _ =>
            sendNotificationToSQS(queue = queue, message = newBibRecord)
          }

          val expectedTransformable =
            SierraTransformable(bibRecord = newBibRecord)

          eventually {
            assertStoredAndSent(
              Version(oldTransformable.sierraId.withoutCheckDigit, 1),
              expectedTransformable,
              sourceVHS,
              messageSender
            )

            messageSender.messages.size shouldBe 1
          }
      }
    }
  }

  it("does not update a bib if an older version is sent to SQS") {
    val newBibRecord = createSierraBibRecordWith(
      modifiedDate = newerDate
    )

    val expectedTransformable =
      SierraTransformable(bibRecord = newBibRecord)
    val key = Version(expectedTransformable.sierraId.withoutCheckDigit, 0)

    val sourceVHS = createSourceVHSWith(
      initialEntries = Map(key -> expectedTransformable)
    )

    withLocalSqsQueuePair() {
      case QueuePair(queue, dlq) =>
        withWorkerService(sourceVHS, queue) {
          case (_, messageSender) =>
            val oldBibRecord = createSierraBibRecordWith(
              id = newBibRecord.id,
              modifiedDate = olderDate
            )

            sendNotificationToSQS(queue = queue, message = oldBibRecord)

            eventually {
              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)

              messageSender.messages shouldBe empty
            }
        }
    }
  }

  it("stores a bib from SQS if the ID already exists but no bibData") {
    val transformable = createSierraTransformableWith(
      maybeBibRecord = None
    )

    val key = Version(transformable.sierraId.withoutCheckDigit, 0)

    val sourceVHS = createSourceVHSWith(
      initialEntries = Map(key -> transformable)
    )

    withLocalSqsQueue() { queue =>
      withWorkerService(sourceVHS, queue) {
        case (_, messageSender) =>
          val bibRecord =
            createSierraBibRecordWith(id = transformable.sierraId)

          sendNotificationToSQS(queue = queue, message = bibRecord)

          val expectedTransformable =
            SierraTransformable(bibRecord = bibRecord)

          eventually {
            val id = Version(transformable.sierraId.withoutCheckDigit, 1)

            assertStoredAndSent(
              id,
              expectedTransformable,
              sourceVHS,
              messageSender
            )

            messageSender
              .getMessages[SierraSourcePayload]
              .map { p => Version(p.id, p.version) } shouldBe Seq(id)
          }
      }
    }
  }

}
