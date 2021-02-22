package weco.catalogue.sierra_linker

import io.circe.{Decoder, Encoder}
import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.{Queue, QueuePair}
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.sierra_adapter.model.{
  AbstractSierraRecord,
  SierraGenerators,
  SierraTypedRecordNumber
}
import uk.ac.wellcome.sierra_adapter.utils.SierraAdapterHelpers
import uk.ac.wellcome.storage.Version
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore

trait SierraLinkerWorkerServiceTestCases[
  Id <: SierraTypedRecordNumber, Record <: AbstractSierraRecord[Id]]
    extends AnyFunSpec
    with Matchers
    with Eventually
    with IntegrationPatience
    with ScalaFutures
    with SierraGenerators
    with LinkerFixtures[Id, Record]
    with SQS
    with SierraAdapterHelpers {

  implicit val decoder: Decoder[Record]
  implicit val encoder: Encoder[Record]

  def withWorkerService[R](queue: Queue,
                           store: VersionedStore[Id, Int, LinkingRecord] =
                             MemoryVersionedStore[Id, LinkingRecord](
                               initialEntries = Map.empty
                             ),
                           messageSender: MemoryMessageSender =
                             new MemoryMessageSender,
                           metrics: MemoryMetrics = new MemoryMetrics)(
    testWith: TestWith[SierraLinkerWorkerService[Id, Record, String], R]): R

  describe("behaves as a SierraLinkerWorkerService") {
    it("reads a Sierra record from SQS and stores it") {
      val bibIds = createSierraBibNumbers(count = 5)

      val bibIds1 = List(bibIds(0), bibIds(1), bibIds(2))

      val record1 = createRecordWith(
        modifiedDate = olderDate,
        bibIds = bibIds1
      )

      val bibIds2 = List(bibIds(2), bibIds(3), bibIds(4))

      val record2 = createRecordWith(
        id = record1.id,
        modifiedDate = newerDate,
        bibIds = bibIds2
      )

      val expectedLink = createLinkingRecord(record1)
        .update(
          id = record2.id,
          newBibIds = getBibIds(record2),
          newModifiedDate = record2.modifiedDate)
        .get

      val store = MemoryVersionedStore[Id, LinkingRecord](
        initialEntries = Map(
          Version(record1.id, 1) -> createLinkingRecord(record1)
        )
      )

      val messageSender = new MemoryMessageSender

      withLocalSqsQueue() { queue =>
        withWorkerService(queue, store, messageSender = messageSender) { _ =>
          sendNotificationToSQS(queue = queue, message = record2)

          eventually {
            messageSender.getMessages[Record] shouldBe Seq(
              updateRecord(
                record2,
                unlinkedBibIds = expectedLink.unlinkedBibIds)
            )
          }
        }
      }
    }

    it("always forwards the newest item, even if it's sent multiple times") {
      val record = createRecord

      val messageSender = new MemoryMessageSender

      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withWorkerService(queue, messageSender = messageSender) { _ =>
            (1 to 5).foreach { _ =>
              sendNotificationToSQS(queue = queue, message = record)
            }

            eventually {
              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)

              messageSender.getMessages[Record] shouldBe (1 to 5).map { _ =>
                record
              }
            }
          }
      }
    }

    it("skips an item which is older than the stored link") {
      val bibIds = createSierraBibNumbers(count = 5)

      val bibIds1 = List(bibIds(0), bibIds(1), bibIds(2))

      val record1 = createRecordWith(
        modifiedDate = olderDate,
        bibIds = bibIds1
      )

      val bibIds2 = List(bibIds(2), bibIds(3), bibIds(4))

      val record2 = createRecordWith(
        id = record1.id,
        modifiedDate = newerDate,
        bibIds = bibIds2
      )

      val store = MemoryVersionedStore[Id, LinkingRecord](
        initialEntries = Map(
          Version(record2.id, 1) -> createLinkingRecord(record2)
        )
      )

      val messageSender = new MemoryMessageSender

      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withWorkerService(queue, store, messageSender = messageSender) { _ =>
            sendNotificationToSQS(queue = queue, message = record1)

            eventually {
              assertQueueEmpty(queue)
              assertQueueEmpty(dlq)

              messageSender.messages shouldBe empty
            }
          }
      }
    }

    it("records a failure if it receives an invalid message") {
      val metrics = new MemoryMetrics()
      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withWorkerService(queue, metrics = metrics) { _ =>
            val body =
              """
                |{
                | "something": "something"
                |}
                  """.stripMargin

            sendNotificationToSQS(queue = queue, body = body)

            eventually {
              assertQueueEmpty(queue)
              assertQueueHasSize(dlq, size = 1)
              metrics.incrementedCounts.find {
                _.endsWith("_ProcessMessage_failure")
              } shouldBe None
            }
          }
      }
    }
  }
}
