package weco.pipeline.sierra_linker

import org.scalatest.EitherValues
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.messaging.memory.MemoryMessageSender
import weco.storage.store.memory.MemoryVersionedStore
import weco.catalogue.source_model.generators.SierraRecordGenerators
import weco.catalogue.source_model.sierra._
import weco.catalogue.source_model.Implicits._
import weco.pipeline.sierra_linker.fixtures.WorkerFixture
import weco.pipeline.sierra_linker.models.Link
import weco.sierra.models.identifiers.{
  SierraHoldingsNumber,
  SierraItemNumber,
  SierraOrderNumber
}

class SierraLinkerFeatureTest
    extends AnyFunSpec
    with Matchers
    with Eventually
    with EitherValues
    with IntegrationPatience
    with SierraRecordGenerators
    with WorkerFixture {

  it("links item records") {
    val messageSender = new MemoryMessageSender

    val record = createSierraItemRecordWith(
      bibIds = List(createSierraBibNumber)
    )

    val expectedLink = Link(record)

    val store = MemoryVersionedStore[SierraItemNumber, Link](
      initialEntries = Map.empty
    )

    withLocalSqsQueue() {
      queue =>
        withItemWorker(queue, store = store, messageSender = messageSender) {
          _ =>
            sendNotificationToSQS(queue, record)

            eventually {
              messageSender.getMessages[SierraItemRecord] shouldBe Seq(record)

              store.getLatest(record.id).value.identifiedT shouldBe expectedLink
            }
        }
    }
  }

  it("links holdings records") {
    val messageSender = new MemoryMessageSender

    val record = createSierraHoldingsRecordWith(
      bibIds = List(createSierraBibNumber)
    )

    val expectedLink = Link(record)

    val store = MemoryVersionedStore[SierraHoldingsNumber, Link](
      initialEntries = Map.empty
    )

    withLocalSqsQueue() {
      queue =>
        withHoldingsWorker(
          queue,
          store = store,
          messageSender = messageSender
        ) {
          _ =>
            sendNotificationToSQS(queue, record)

            eventually {
              messageSender.getMessages[SierraHoldingsRecord] shouldBe Seq(
                record
              )

              store.getLatest(record.id).value.identifiedT shouldBe expectedLink
            }
        }
    }
  }

  it("links order records") {
    val messageSender = new MemoryMessageSender

    val record = createSierraOrderRecordWith(
      bibIds = List(createSierraBibNumber)
    )

    val expectedLink = Link(record)

    val store = MemoryVersionedStore[SierraOrderNumber, Link](
      initialEntries = Map.empty
    )

    withLocalSqsQueue() {
      queue =>
        withOrderWorker(queue, store = store, messageSender = messageSender) {
          _ =>
            sendNotificationToSQS(queue, record)

            eventually {
              messageSender.getMessages[SierraOrderRecord] shouldBe Seq(record)

              store.getLatest(record.id).value.identifiedT shouldBe expectedLink
            }
        }
    }
  }
}
