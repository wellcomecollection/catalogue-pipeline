package uk.ac.wellcome.platform.sierra_items_to_dynamo

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.platform.sierra_items_to_dynamo.fixtures.WorkerServiceFixture
import uk.ac.wellcome.sierra_adapter.model.{SierraGenerators, SierraItemRecord}
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers
import uk.ac.wellcome.storage.Version

class SierraItemsToDynamoFeatureTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with SierraAdapterHelpers
    with SierraGenerators
    with WorkerServiceFixture {

  it("reads items from Sierra and adds them to DynamoDB") {
    val store = createStore[SierraItemRecord]()
    withLocalSqsQueue() { queue =>
      withWorkerService(queue, store) {
        case (_, messageSender) =>
          val itemRecord = createSierraItemRecordWith(
            bibIds = List(createSierraBibNumber)
          )

          sendNotificationToSQS(
            queue = queue,
            message = itemRecord
          )

          eventually {
            assertStoredAndSent(
              id = Version(itemRecord.id.withoutCheckDigit, 0),
              t = itemRecord,
              store = store,
              messageSender
            )
          }
      }
    }
  }

}
