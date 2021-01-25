package uk.ac.wellcome.platform.router

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.messaging.sqs.SQSStream
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal.WorkState.{Denormalised, Merged}
import uk.ac.wellcome.models.work.internal.{CollectionPath, Relations, Work}
import uk.ac.wellcome.pipeline_storage._

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class RouterWorkerServiceTest
    extends AnyFunSpec
    with WorkGenerators
    with SQS
    with Akka
    with Eventually
    with IntegrationPatience {
  val pipelineStorageConfig = PipelineStorageConfig(
    batchSize = 1,
    flushInterval = 1 milliseconds,
    parallelism = 1
  )
  it("sends collectionPath to paths topic") {
    val work = mergedWork().collectionPath(CollectionPath("a"))
    val indexer = new MemoryIndexer[Work[Denormalised]]()

    val retriever = new MemoryRetriever[Work[Merged]](
      index = mutable.Map(work.id -> work)
    )

    withWorkerService(indexer, retriever) {
      case (QueuePair(queue, dlq), worksMessageSender, pathsMessageSender) =>
        sendNotificationToSQS(queue = queue, body = work.id)
        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
          pathsMessageSender.messages.map(_.body) should contain("a")
          worksMessageSender.messages shouldBe empty
          indexer.index shouldBe empty
        }
    }
  }

  it("sends a work without collectionPath to works topic") {
    val work = mergedWork()
    val indexer = new MemoryIndexer[Work[Denormalised]]()

    val retriever = new MemoryRetriever[Work[Merged]](
      index = mutable.Map(work.id -> work)
    )

    withWorkerService(indexer, retriever) {
      case (QueuePair(queue, dlq), worksMessageSender, pathsMessageSender) =>
        sendNotificationToSQS(queue = queue, body = work.id)

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
          worksMessageSender.messages.map(_.body) should contain(work.id)
          pathsMessageSender.messages shouldBe empty
          indexer.index should contain(
            work.id -> work.transition[Denormalised](Relations.none))
        }
    }
  }

  it("sends on an invisible work") {
    val work =
      mergedWork().collectionPath(CollectionPath("a/2")).invisible()

    val indexer = new MemoryIndexer[Work[Denormalised]]()

    val retriever = new MemoryRetriever[Work[Merged]](
      index = mutable.Map(work.id -> work)
    )

    withWorkerService(indexer, retriever) {
      case (QueuePair(queue, dlq), worksMessageSender, pathsMessageSender) =>
        sendNotificationToSQS(queue = queue, body = work.id)

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
          worksMessageSender.messages shouldBe empty
          pathsMessageSender.messages.map(_.body) should contain("a/2")
          indexer.index shouldBe empty
        }
    }
  }

  it(
    "sends the message to the dlq and doesn't send anything on if indexing fails") {
    val work = mergedWork()
    val failingIndexer = new Indexer[Work[Denormalised]] {
      override def init(): Future[Unit] = Future.successful(())
      override def apply(documents: Seq[Work[Denormalised]])
        : Future[Either[Seq[Work[Denormalised]], Seq[Work[Denormalised]]]] =
        Future.successful(Left(documents))
    }

    val retriever = new MemoryRetriever[Work[Merged]](
      index = mutable.Map(work.id -> work)
    )

    withWorkerService(failingIndexer, retriever) {
      case (QueuePair(queue, dlq), worksMessageSender, pathsMessageSender) =>
        sendNotificationToSQS(queue = queue, body = work.id)

        eventually {
          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, size = 1)
          worksMessageSender.messages shouldBe empty
          pathsMessageSender.messages shouldBe empty
        }
    }
  }

  def withWorkerService[R](indexer: Indexer[Work[Denormalised]],
                           retriever: Retriever[Work[Merged]])(
    testWith: TestWith[(QueuePair, MemoryMessageSender, MemoryMessageSender),
                       R]): R =
    withLocalSqsQueuePair(visibilityTimeout = 1) {
      case q @ QueuePair(queue, _) =>
        val worksMessageSender = new MemoryMessageSender
        val pathsMessageSender = new MemoryMessageSender
        withActorSystem { implicit ac =>
          withSQSStream(
            queue = queue,
          ) { stream: SQSStream[NotificationMessage] =>
            val service =
              new RouterWorkerService(
                indexer = indexer,
                messageSender = worksMessageSender,
                pathsMsgSender = pathsMessageSender,
                workRetriever = retriever,
                msgStream = stream,
                config = pipelineStorageConfig
              )
            service.run()
            testWith((q, worksMessageSender, pathsMessageSender))
          }
        }
    }
}
