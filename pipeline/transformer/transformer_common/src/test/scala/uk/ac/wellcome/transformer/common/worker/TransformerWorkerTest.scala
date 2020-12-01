package uk.ac.wellcome.transformer.common.worker

import org.scalatest.concurrent.{Eventually, IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.json.JsonUtil._
import uk.ac.wellcome.messaging.fixtures.SQS.{Queue, QueuePair}
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal.WorkState.Source
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import uk.ac.wellcome.pipeline_storage.{MemoryIndexer, PipelineStorageStream}
import uk.ac.wellcome.storage.store.VersionedStore
import uk.ac.wellcome.storage.store.memory.MemoryVersionedStore
import uk.ac.wellcome.storage.{Identified, ReadError, Version}

import scala.collection.mutable
import scala.concurrent.Future
import scala.util.{Failure, Try}

trait TestData
case object ValidTestData extends TestData
case object InvalidTestData extends TestData

object TestTransformer extends Transformer[TestData] with WorkGenerators {
  def apply(data: TestData,
            version: Int): Either[Exception, Work.Visible[Source]] =
    data match {
      case ValidTestData   => Right(sourceWork())
      case InvalidTestData => Left(new Exception("No No No"))
    }
}

class TestTransformerWorker(
  val pipelineStream: PipelineStorageStream[NotificationMessage,
                                            Work[Source],
                                            String],
  store: VersionedStore[String, Int, TestData]
) extends TransformerWorker[TestData, String] {
  val transformer: Transformer[TestData] = TestTransformer

  override protected def lookupSourceData(
    key: StoreKey): Either[ReadError, TestData] =
    store.getLatest(key.id).map { case Identified(_, testData) => testData }
}

class TransformerWorkerTest
    extends AnyFunSpec
    with ScalaFutures
    with Matchers
    with Eventually
    with IntegrationPatience
    with PipelineStorageStreamFixtures {

  describe("it sends a transformed work") {
    def withTransformedWork[R](
      testWith: TestWith[(QueuePair,
                          MemoryIndexer[Work[Source]],
                          MemoryMessageSender),
                         R]): R = {
      val records = Map(
        Version("A", 1) -> ValidTestData,
        Version("B", 2) -> ValidTestData,
        Version("C", 3) -> ValidTestData
      )

      val workIndexer =
        new MemoryIndexer[Work[Source]](
          index = mutable.Map[String, Work[Source]]())
      val workKeySender = new MemoryMessageSender()

      withLocalSqsQueuePair() {
        case queuePair @ QueuePair(queue, _) =>
          withWorker(
            queue,
            workIndexer = workIndexer,
            workKeySender = workKeySender,
            records = records) { _ =>
            sendNotificationToSQS(queue, Version("A", 1))
            sendNotificationToSQS(queue, Version("B", 2))
            sendNotificationToSQS(queue, Version("C", 3))

            testWith(
              (queuePair, workIndexer, workKeySender))
          }
      }
    }

    it("empties the queue") {
      withTransformedWork {
        case (QueuePair(queue, dlq), _, _) =>
          eventually {
            assertQueueEmpty(dlq)
            assertQueueEmpty(queue)
          }
      }
    }

    it("indexes the work and sends the index IDs") {
      withTransformedWork {
        case (_, workIndexer, workKeySender) =>
          eventually {
            workIndexer.index should have size 3
            workKeySender.messages.map { _.body } should contain theSameElementsAs workIndexer.index.keys
          }
      }
    }
  }

  describe("sends failures to the DLQ") {
    it("if it can't parse the JSON on the queue") {
      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withWorker(queue) { _ =>
            sendInvalidJSONto(queue)

            eventually {
              assertQueueHasSize(dlq, size = 1)
              assertQueueEmpty(queue)
            }
          }
      }
    }

    it("if it can't parse the notification as a Version[String, Int]") {
      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withWorker(queue) { _ =>
            sendNotificationToSQS(queue, "not-a-version")

            eventually {
              assertQueueHasSize(dlq, size = 1)
              assertQueueEmpty(queue)
            }
          }
      }
    }

    it("if it can't find the source record in the store") {
      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withWorker(queue) { _ =>
            sendNotificationToSQS(queue, Version("A", 1))

            eventually {
              assertQueueHasSize(dlq, size = 1)
              assertQueueEmpty(queue)
            }
          }
      }
    }

    it("if the work can't be transformed") {
      val records = Map(
        Version("A", 1) -> ValidTestData,
        Version("B", 2) -> ValidTestData,
        Version("C", 3) -> InvalidTestData
      )

      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withWorker(queue, records = records) { _ =>
            sendNotificationToSQS(queue, Version("A", 1))
            sendNotificationToSQS(queue, Version("B", 2))
            sendNotificationToSQS(queue, Version("C", 3))

            eventually {
              assertQueueHasSize(dlq, size = 1)
              assertQueueEmpty(queue)
            }
          }
      }
    }

    it("if it can't index the work") {
      val brokenIndexer = new MemoryIndexer[Work[Source]](
        index = mutable.Map[String, Work[Source]]()
      ) {
        override def index(documents: Seq[Work[Source]])
          : Future[Either[Seq[Work[Source]], Seq[Work[Source]]]] =
          Future.failed(new Throwable("BOOM!"))
      }

      val workKeySender = new MemoryMessageSender

      val records = Map(Version("A", 1) -> ValidTestData)

      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withWorker(
            queue,
            records = records,
            workIndexer = brokenIndexer,
            workKeySender = workKeySender) { _ =>
            sendNotificationToSQS(queue, Version("A", 1))

            eventually {
              assertQueueEmpty(queue)
              assertQueueHasSize(dlq, size = 1)

              workKeySender.messages shouldBe empty
            }
          }
      }
    }

    it("if it can't send the key of the indexed work") {
      val brokenSender = new MemoryMessageSender() {
        override def send(body: String): Try[Unit] =
          Failure(new Throwable("BOOM!"))
      }

      val records = Map(Version("A", 1) -> ValidTestData)

      withLocalSqsQueuePair() {
        case QueuePair(queue, dlq) =>
          withWorker(queue, records = records, workKeySender = brokenSender) {
            _ =>
              sendNotificationToSQS(queue, Version("A", 1))

              eventually {
                assertQueueEmpty(queue)
                assertQueueHasSize(dlq, size = 1)
              }
          }
      }
    }
  }

  def withWorker[R](
    queue: Queue,
    records: Map[Version[String, Int], TestData] = Map.empty,
    workIndexer: MemoryIndexer[Work[Source]] = new MemoryIndexer[Work[Source]](
      index = mutable.Map[String, Work[Source]]()),
    workKeySender: MemoryMessageSender = new MemoryMessageSender()
  )(
    testWith: TestWith[Unit, R]
  ): R =
    withPipelineStream[Work[Source], R](
      queue = queue,
      indexer = workIndexer,
      sender = workKeySender) { pipelineStream =>
      val store = MemoryVersionedStore[String, TestData](records)

      val worker = new TestTransformerWorker(
        pipelineStream = pipelineStream,
        store = store
      )

      worker.run()
      testWith(())
    }
}
