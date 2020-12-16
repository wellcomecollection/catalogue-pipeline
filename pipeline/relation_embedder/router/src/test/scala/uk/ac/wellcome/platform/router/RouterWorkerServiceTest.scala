package uk.ac.wellcome.platform.router

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal.WorkState.{Denormalised, Merged}
import uk.ac.wellcome.models.work.internal.{CollectionPath, Relations, Work}
import uk.ac.wellcome.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import uk.ac.wellcome.pipeline_storage.{
  Indexer,
  MemoryIndexer,
  MemoryRetriever,
  Retriever
}

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class RouterWorkerServiceTest
    extends AnyFunSpec
    with WorkGenerators
    with PipelineStorageStreamFixtures
    with Eventually
    with IntegrationPatience {

  it("sends collectionPath to paths topic") {
    val work = mergedWork().collectionPath(CollectionPath("a"))
    val indexer = new MemoryIndexer[Work[Denormalised]]()

    val retriever = new MemoryRetriever[Work[Merged]](
      index = mutable.Map(work.id -> work)
    )

    withWorkerService(indexer, retriever) {
      case (
          QueuePair(queue, dlq),
          worksMessageSender,
          pathsMessageSender) =>
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
      case (
          QueuePair(queue, dlq),
          worksMessageSender,
          pathsMessageSender) =>
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
      case (
          QueuePair(queue, dlq),
          worksMessageSender,
          pathsMessageSender) =>
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
      override def index(documents: Seq[Work[Denormalised]])
        : Future[Either[Seq[Work[Denormalised]], Seq[Work[Denormalised]]]] =
        Future.successful(Left(documents))
    }

    val retriever = new MemoryRetriever[Work[Merged]](
      index = mutable.Map(work.id -> work)
    )

    withWorkerService(failingIndexer, retriever) {
      case (
          QueuePair(queue, dlq),
          worksMessageSender,
          pathsMessageSender) =>
        sendNotificationToSQS(queue = queue, body = work.id)

        eventually {
          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, size = 1)
          worksMessageSender.messages shouldBe empty
          pathsMessageSender.messages shouldBe empty
        }
    }
  }

  def withWorkerService[R](
    indexer: Indexer[Work[Denormalised]],
    retriever: Retriever[Work[Merged]])(
    testWith: TestWith[(QueuePair,
                        MemoryMessageSender,
                        MemoryMessageSender),
                       R]): R =
    withLocalSqsQueuePair(visibilityTimeout = 1) {
      case q @ QueuePair(queue, _) =>
        val worksMessageSender = new MemoryMessageSender
        val pathsMessageSender = new MemoryMessageSender

        withPipelineStream(
          queue = queue,
          indexer = indexer,
          sender = worksMessageSender
        ) { pipelineStream =>
          val service =
            new RouterWorkerService(
              pathsMsgSender = pathsMessageSender,
              workRetriever = retriever,
              pipelineStream = pipelineStream
            )
          service.run()
          testWith((q, worksMessageSender, pathsMessageSender))
        }
    }
}
