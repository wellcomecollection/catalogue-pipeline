package uk.ac.wellcome.platform.sierra_bib_merger

import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.mockito.MockitoSugar
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.platform.sierra_bib_merger.fixtures.WorkerServiceFixture
import uk.ac.wellcome.sierra_adapter.model.{SierraGenerators, SierraTransformable}
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers
import uk.ac.wellcome.storage.Version

class SierraBibMergerFeatureTest
  extends AnyFunSpec
    with Matchers
    with Eventually
    with MockitoSugar
    with IntegrationPatience
    with ScalaFutures
    with SQS
    with SierraGenerators
    with SierraAdapterHelpers
    with WorkerServiceFixture {

  it("stores a bib in the hybrid store") {
    val store = createStore[SierraTransformable]()
    withLocalSqsQueue { queue =>
      withWorkerService(store, queue) { case (_, messageSender) =>
        val bibRecord = createSierraBibRecord

        sendNotificationToSQS(queue = queue, message = bibRecord)

        val expectedSierraTransformable =
          SierraTransformable(bibRecord = bibRecord)

        eventually {
          assertStoredAndSent(
            Version(expectedSierraTransformable.sierraId.withoutCheckDigit, 0),
            expectedSierraTransformable,
            store, messageSender
          )
        }
      }
    }
  }

  it("stores multiple bibs from SQS") {
    val store = createStore[SierraTransformable]()
    withLocalSqsQueue { queue =>
      withWorkerService(store, queue) { case (_, messageSender) =>
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
            Version(expectedTransformable1.sierraId.withoutCheckDigit, 0),
            expectedTransformable1,
            store,
            messageSender
          )
          assertStoredAndSent(
            Version(expectedTransformable2.sierraId.withoutCheckDigit, 0),
            expectedTransformable2,
            store,
            messageSender
          )
        }
      }
    }
  }

  it("updates a bib if a newer version is sent to SQS") {
    val oldBibRecord = createSierraBibRecordWith(
      modifiedDate = olderDate
    )

    val oldTransformable =
      SierraTransformable(bibRecord = oldBibRecord)
    val store = createStore[SierraTransformable](Map(Version(oldTransformable.sierraId.withoutCheckDigit, 0) -> oldTransformable))
    withLocalSqsQueue { queue =>
      withWorkerService(store, queue) { case (_, messageSender) =>

        val newBibRecord = createSierraBibRecordWith(
          id = oldBibRecord.id,
          modifiedDate = newerDate
        )

        sendNotificationToSQS(queue = queue, message = newBibRecord)

        val expectedTransformable =
          SierraTransformable(bibRecord = newBibRecord)

        eventually {
          assertStoredAndSent(
            Version(oldTransformable.sierraId.withoutCheckDigit, 1),
            expectedTransformable,
            store,
            messageSender
          )
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
    val store = createStore[SierraTransformable](Map(key -> expectedTransformable))
    withLocalSqsQueue { queue =>
      withWorkerService(store, queue) { case (_, messageSender) =>

        val oldBibRecord = createSierraBibRecordWith(
          id = newBibRecord.id,
          modifiedDate = olderDate
        )

        sendNotificationToSQS(queue = queue, message = oldBibRecord)

        // Wait so there's enough time for this update to have gone through (if it was going to).
        Thread.sleep(5000)

        assertStoredAndSent(
          key.copy(version = 1),
          expectedTransformable,
          store, messageSender
        )
      }
    }
  }


  it("stores a bib from SQS if the ID already exists but no bibData") {
    val transformable = createSierraTransformableWith(
      maybeBibRecord = None
    )
    val store = createStore[SierraTransformable](Map(Version(transformable.sierraId.withoutCheckDigit, 0) -> transformable))
    withLocalSqsQueue { queue =>
      withWorkerService(store, queue) { case (_, messageSender) =>

        val bibRecord =
          createSierraBibRecordWith(id = transformable.sierraId)

        sendNotificationToSQS(queue = queue, message = bibRecord)

        val expectedTransformable =
          SierraTransformable(bibRecord = bibRecord)

        eventually {
          assertStoredAndSent(
            Version(transformable.sierraId.withoutCheckDigit, 1),
            expectedTransformable,
            store,
            messageSender
          )
        }
      }
    }
  }

}
