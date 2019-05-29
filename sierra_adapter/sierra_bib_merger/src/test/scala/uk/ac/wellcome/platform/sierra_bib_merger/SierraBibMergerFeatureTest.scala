package uk.ac.wellcome.platform.sierra_bib_merger

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.{FunSpec, Matchers}
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.transformable.SierraTransformable
import uk.ac.wellcome.models.transformable.sierra.test.utils.SierraGenerators
import uk.ac.wellcome.platform.sierra_bib_merger.fixtures.WorkerServiceFixture

class SierraBibMergerFeatureTest
    extends FunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with SQS
    with SierraGenerators
    with WorkerServiceFixture {

  it("stores a bib in the hybrid store") {
    val dao = createDao
    val store = createStore
    val vhs = createVhs(dao, store)

    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue { queue =>
      withWorkerService(vhs, queue, messageSender) { _ =>
        val bibRecord = createSierraBibRecord

        sendNotificationToSQS(queue = queue, message = bibRecord)

        val expectedSierraTransformable =
          SierraTransformable(bibRecord = bibRecord)

        eventually {
          assertStoredAndSent(
            transformable = expectedSierraTransformable,
            messageSender = messageSender,
            dao = dao,
            vhs = vhs
          )
        }
      }
    }
  }

  it("stores multiple bibs from SQS") {
    val dao = createDao
    val store = createStore
    val vhs = createVhs(dao, store)

    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue { queue =>
      withWorkerService(vhs, queue, messageSender) { _ =>
        val record1 = createSierraBibRecord
        sendNotificationToSQS(queue = queue, message = record1)

        val expectedTransformable1 =
          SierraTransformable(bibRecord = record1)

        val record2 = createSierraBibRecord
        sendNotificationToSQS(queue = queue, message = record2)

        val expectedTransformable2 =
          SierraTransformable(bibRecord = record2)

        eventually {
          assertStoredAndSent(
            transformable = expectedTransformable1,
            messageSender = messageSender,
            dao = dao,
            vhs = vhs
          )
          assertStoredAndSent(
            transformable = expectedTransformable2,
            messageSender = messageSender,
            dao = dao,
            vhs = vhs
          )
        }
      }
    }
  }

  it("updates a bib if a newer version is sent to SQS") {
    val dao = createDao
    val store = createStore
    val vhs = createVhs(dao, store)

    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue { queue =>
      withWorkerService(vhs, queue, messageSender) { _ =>
        val oldBibRecord = createSierraBibRecordWith(
          modifiedDate = olderDate
        )

        val oldTransformable =
          SierraTransformable(bibRecord = oldBibRecord)

        val newBibRecord = createSierraBibRecordWith(
          id = oldBibRecord.id,
          modifiedDate = newerDate
        )

        storeInVHS(oldTransformable, vhs = vhs)
        sendNotificationToSQS(queue = queue, message = newBibRecord)

        val expectedTransformable =
          SierraTransformable(bibRecord = newBibRecord)

        eventually {
          assertStoredAndSent(
            transformable = expectedTransformable,
            messageSender = messageSender,
            dao = dao,
            vhs = vhs
          )
        }
      }
    }
  }

  it("does not update a bib if an older version is sent to SQS") {
    val dao = createDao
    val store = createStore
    val vhs = createVhs(dao, store)

    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue { queue =>
      withWorkerService(vhs, queue, messageSender) { _ =>
        val newBibRecord = createSierraBibRecordWith(
          modifiedDate = newerDate
        )

        val expectedTransformable =
          SierraTransformable(bibRecord = newBibRecord)

        val oldBibRecord = createSierraBibRecordWith(
          id = newBibRecord.id,
          modifiedDate = olderDate
        )

        storeInVHS(expectedTransformable, vhs = vhs)
        sendNotificationToSQS(queue = queue, message = oldBibRecord)

        // Wait for this update to have gone through (if it was going to).
        Thread.sleep(1000)

        assertStoredAndSent(
          transformable = expectedTransformable,
          messageSender = messageSender,
          dao = dao,
          vhs = vhs
        )
      }
    }
  }

  it("stores a bib from SQS if the ID already exists but no bibData") {
    val dao = createDao
    val store = createStore
    val vhs = createVhs(dao, store)

    val messageSender = new MemoryMessageSender()

    withLocalSqsQueue { queue =>
      withWorkerService(vhs, queue, messageSender) { _ =>
        val transformable = createSierraTransformableWith(
          maybeBibRecord = None
        )

        val bibRecord =
          createSierraBibRecordWith(id = transformable.sierraId)

        storeInVHS(transformable, vhs = vhs)
        sendNotificationToSQS(queue = queue, message = bibRecord)

        val expectedTransformable =
          SierraTransformable(bibRecord = bibRecord)

        eventually {
          assertStoredAndSent(
            transformable = expectedTransformable,
            messageSender = messageSender,
            dao = dao,
            vhs = vhs
          )
        }
      }
    }
  }
}
