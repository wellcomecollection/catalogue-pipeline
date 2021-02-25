package weco.catalogue.sierra_linker

import org.scalatest.EitherValues
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.sierra_adapter.model.Implicits._
import uk.ac.wellcome.sierra_adapter.model._
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore
import weco.catalogue.sierra_adapter.generators.SierraGenerators
import weco.catalogue.sierra_linker.fixtures.WorkerFixture
import weco.catalogue.sierra_linker.models.Link

class SierraLinkerFeatureTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with EitherValues
    with IntegrationPatience
    with SierraGenerators
    with WorkerFixture {

  it("reads items from SQS, stores the link, and sends the record onward") {
    val messageSender = new MemoryMessageSender

    val record = createSierraItemRecordWith(
      bibIds = List(createSierraBibNumber)
    )

    val expectedLink = Link(record)

    val store = MemoryVersionedStore[SierraItemNumber, Link](
      initialEntries = Map.empty
    )

    withLocalSqsQueue() { queue =>
      withItemWorker(queue, store = store, messageSender = messageSender) { _ =>
        sendNotificationToSQS(queue, record)

        eventually {
          messageSender.getMessages[SierraItemRecord] shouldBe Seq(record)

          store.getLatest(record.id).value.identifiedT shouldBe expectedLink
        }
      }
    }
  }

  it("reads holdings from SQS, stores the link, and sends the record onward") {
    val messageSender = new MemoryMessageSender

    val record = createSierraHoldingsRecordWith(
      bibIds = List(createSierraBibNumber)
    )

    val expectedLink = Link(record)

    val store = MemoryVersionedStore[SierraHoldingsNumber, Link](
      initialEntries = Map.empty
    )

    withLocalSqsQueue() { queue =>
      withHoldingsWorker(queue, store = store, messageSender = messageSender) {
        _ =>
          sendNotificationToSQS(queue, record)

          eventually {
            messageSender.getMessages[SierraHoldingsRecord] shouldBe Seq(record)

            store.getLatest(record.id).value.identifiedT shouldBe expectedLink
          }
      }
    }
  }
}
