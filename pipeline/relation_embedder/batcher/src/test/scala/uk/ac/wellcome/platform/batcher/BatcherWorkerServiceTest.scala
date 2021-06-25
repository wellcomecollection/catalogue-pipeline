package uk.ac.wellcome.platform.batcher

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Try}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.concurrent.Eventually
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.time.{Seconds, Span}
import io.circe.Encoder

import weco.fixtures.TestWith
import weco.akka.fixtures.Akka
import weco.messaging.fixtures.SQS
import weco.messaging.memory.MemoryMessageSender
import weco.messaging.sns.NotificationMessage
import weco.json.JsonUtil._

import SQS.QueuePair

class BatcherWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with SQS
    with Akka
    with Eventually {

  import Selector._

  /** The following tests use paths representing this tree:
    *
    * A
    * |
    * |-------------
    * |  |         |
    * B  C         E
    * |  |------   |---------
    * |  |  |  |   |  |  |  |
    * D  X  Y  Z   1  2  3  4
    */
  it("processes incoming paths into batches") {
    withWorkerService() {
      case (QueuePair(queue, dlq), msgSender) =>
        sendNotificationToSQS(queue = queue, body = "A/B")
        sendNotificationToSQS(queue = queue, body = "A/E/1")
        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
        val batches = msgSender.getMessages[Batch]
        batches.size shouldBe 1
        batchRoots(batches) shouldBe Set("A")
        batches.head.selectors should contain theSameElementsAs List(
          Node("A"),
          Children("A"),
          Children("A/E"),
          Descendents("A/B"),
          Descendents("A/E/1"),
        )
    }
  }

  it("processes incoming paths into batches split per tree") {
    withWorkerService() {
      case (QueuePair(queue, dlq), msgSender) =>
        sendNotificationToSQS(queue = queue, body = "A")
        sendNotificationToSQS(queue = queue, body = "Other/Tree")
        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
        val batches = msgSender.getMessages[Batch]
        batches.size shouldBe 2
        batchRoots(batches) shouldBe Set("A", "Other")
        batchWithRoot("A", batches) should contain theSameElementsAs List(
          Tree("A"))
        batchWithRoot("Other", batches) should contain theSameElementsAs List(
          Node("Other"),
          Children("Other"),
          Descendents("Other/Tree")
        )
    }
  }

  it("sends the whole tree when batch consists of too many selectors") {
    withWorkerService(3) {
      case (QueuePair(queue, dlq), msgSender) =>
        sendNotificationToSQS(queue = queue, body = "A/B")
        sendNotificationToSQS(queue = queue, body = "A/E/1")
        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
        val batches = msgSender.getMessages[Batch]
        batches.size shouldBe 1
        batches.head shouldBe Batch(rootPath = "A", selectors = List(Tree("A")))
    }
  }

  it("doesn't delete paths where selectors failed sending ") {
    withWorkerService(
      brokenPaths = Set("A/E", "A/B"),
      flushInterval = 750 milliseconds) {
      case (QueuePair(queue, dlq), msgSender) =>
        sendNotificationToSQS(queue = queue, body = "A/E")
        sendNotificationToSQS(queue = queue, body = "A/B")
        sendNotificationToSQS(queue = queue, body = "A/E/1")
        sendNotificationToSQS(queue = queue, body = "Other/Tree")
        eventually(Timeout(Span(10, Seconds))) {
          assertQueueEmpty(queue)
        }
        val failedPaths = getMessages(dlq)
          .map(msg => fromJson[NotificationMessage](msg.body).get.body)
          .toList
        failedPaths should contain theSameElementsAs List("A/E", "A/B")
        val sentBatches = msgSender.getMessages[Batch]
        sentBatches.size shouldBe 1
        batchRoots(sentBatches) shouldBe Set("Other")
        batchWithRoot("Other", sentBatches) should contain theSameElementsAs List(
          Node("Other"),
          Children("Other"),
          Descendents("Other/Tree")
        )
    }
  }

  def batchRoots(batches: Seq[Batch]): Set[String] =
    batches.map(_.rootPath).toSet

  def batchWithRoot(rootPath: String, batches: Seq[Batch]): List[Selector] =
    batches.find(_.rootPath == rootPath).get.selectors

  def withWorkerService[R](maxBatchSize: Int = 10,
                           brokenPaths: Set[String] = Set.empty,
                           flushInterval: FiniteDuration = 100 milliseconds)(
    testWith: TestWith[(QueuePair, MemoryMessageSender), R]): R =
    withLocalSqsQueuePair() { queuePair =>
      withActorSystem { implicit actorSystem =>
        withSQSStream[NotificationMessage, R](queuePair.queue) { msgStream =>
          val msgSender = new MessageSender(brokenPaths)
          val workerService = new BatcherWorkerService[String](
            msgStream = msgStream,
            msgSender = msgSender,
            flushInterval = flushInterval,
            maxBatchSize = maxBatchSize
          )
          workerService.run()
          testWith((queuePair, msgSender))
        }
      }
    }

  class MessageSender(brokenPaths: Set[String] = Set.empty)
      extends MemoryMessageSender {
    override def sendT[T](t: T)(implicit encoder: Encoder[T]): Try[Unit] = {
      val batch = fromJson[Batch](toJson(t).get).get
      if (batch.selectors.map(_.path).exists(brokenPaths.contains))
        Failure(new Exception("Broken"))
      else
        super.sendT(t)
    }
  }
}
