package weco.pipeline.router

import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import weco.catalogue.internal_model.identifiers.IdentifierType
import weco.catalogue.internal_model.work.WorkState.{Denormalised, Merged}
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.catalogue.internal_model.work.{CollectionPath, Relations, Work}
import weco.fixtures.TestWith
import weco.messaging.fixtures.SQS.QueuePair
import weco.messaging.memory.MemoryMessageSender
import weco.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import weco.pipeline_storage.memory.{MemoryIndexer, MemoryRetriever}
import weco.pipeline_storage.{Indexer, Retriever}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

class RouterWorkerServiceTest
    extends AnyFunSpec
    with WorkGenerators
    with PipelineStorageStreamFixtures
    with Eventually
    with IntegrationPatience {

  it("sends collectionPath to paths topic") {
    val work = mergedWork(
      sourceIdentifier =
        createSourceIdentifierWith(IdentifierType.CalmRecordIdentifier)
    ).collectionPath(CollectionPath("a"))
    val indexer = new MemoryIndexer[Work[Denormalised]]()

    val retriever = new MemoryRetriever[Work[Merged]](
      index = mutable.Map(work.id -> work)
    )

    withWorkerService(indexer, retriever) {
      case (
            QueuePair(queue, dlq),
            worksMessageSender,
            pathsMessageSender,
            pathConcatenatorSender
          ) =>
        sendNotificationToSQS(queue = queue, body = work.id)
        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
          pathsMessageSender.messages.map(_.body) should contain("a")
          worksMessageSender.messages shouldBe empty
          pathConcatenatorSender.messages shouldBe empty
          indexer.index shouldBe empty
        }
    }
  }
  it("sends collectionPath to path concatenator topic for sierra works") {
    val work = mergedWork(
      sourceIdentifier =
        createSourceIdentifierWith(IdentifierType.SierraSystemNumber)
    ).collectionPath(CollectionPath("a"))
    val indexer = new MemoryIndexer[Work[Denormalised]]()

    val retriever = new MemoryRetriever[Work[Merged]](
      index = mutable.Map(work.id -> work)
    )

    withWorkerService(indexer, retriever) {
      case (
            QueuePair(queue, dlq),
            worksMessageSender,
            pathsMessageSender,
            pathConcatenatorSender
          ) =>
        sendNotificationToSQS(queue = queue, body = work.id)
        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
          pathsMessageSender.messages shouldBe empty
          worksMessageSender.messages shouldBe empty
          pathConcatenatorSender.messages.map(_.body) should contain("a")
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
            pathsMessageSender,
            pathConcatenatorSender
          ) =>
        sendNotificationToSQS(queue = queue, body = work.id)

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
          worksMessageSender.messages.map(_.body) should contain(work.id)
          pathsMessageSender.messages shouldBe empty
          pathConcatenatorSender.messages shouldBe empty
          indexer.index should contain(
            work.id -> work.transition[Denormalised](Relations.none)
          )
        }
    }
  }

  it("sends on an invisible work") {
    val work =
      mergedWork(
        sourceIdentifier =
          createSourceIdentifierWith(IdentifierType.CalmRecordIdentifier)
      ).collectionPath(CollectionPath("a/2")).invisible()

    val indexer = new MemoryIndexer[Work[Denormalised]]()

    val retriever = new MemoryRetriever[Work[Merged]](
      index = mutable.Map(work.id -> work)
    )

    withWorkerService(indexer, retriever) {
      case (
            QueuePair(queue, dlq),
            worksMessageSender,
            pathsMessageSender,
            pathConcatenatorSender
          ) =>
        sendNotificationToSQS(queue = queue, body = work.id)

        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
          worksMessageSender.messages shouldBe empty
          pathsMessageSender.messages.map(_.body) should contain("a/2")
          pathConcatenatorSender.messages shouldBe empty
          indexer.index shouldBe empty
        }
    }
  }

  it(
    "sends the message to the dlq and doesn't send anything on if indexing fails"
  ) {
    val work = mergedWork()
    val failingIndexer = new Indexer[Work[Denormalised]] {
      override def init(): Future[Unit] = Future.successful(())
      override def apply(
        documents: Seq[Work[Denormalised]]
      ): Future[Either[Seq[Work[Denormalised]], Seq[Work[Denormalised]]]] =
        Future.successful(Left(documents))
    }

    val retriever = new MemoryRetriever[Work[Merged]](
      index = mutable.Map(work.id -> work)
    )

    withWorkerService(failingIndexer, retriever) {
      case (
            QueuePair(queue, dlq),
            worksMessageSender,
            pathsMessageSender,
            pathConcatenatorSender
          ) =>
        sendNotificationToSQS(queue = queue, body = work.id)

        eventually {
          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, size = 1)
          worksMessageSender.messages shouldBe empty
          pathsMessageSender.messages shouldBe empty
          pathConcatenatorSender.messages shouldBe empty
        }
    }
  }

  def withWorkerService[R](
    indexer: Indexer[Work[Denormalised]],
    retriever: Retriever[Work[Merged]]
  )(
    testWith: TestWith[
      (
        QueuePair,
        MemoryMessageSender,
        MemoryMessageSender,
        MemoryMessageSender
      ),
      R
    ]
  ): R =
    withLocalSqsQueuePair(visibilityTimeout = 1 second) {
      case q @ QueuePair(queue, _) =>
        val worksMessageSender = new MemoryMessageSender
        val pathsMessageSender = new MemoryMessageSender
        val pathConcatenatorSender = new MemoryMessageSender
        withPipelineStream(
          queue = queue,
          indexer = indexer,
          sender = worksMessageSender
        ) {
          pipelineStream =>
            val service =
              new RouterWorkerService(
                pathsMsgSender = pathsMessageSender,
                workRetriever = retriever,
                pipelineStream = pipelineStream,
                pathConcatenatorMsgSender = pathConcatenatorSender
              )
            service.run()
            testWith(
              (
                q,
                worksMessageSender,
                pathsMessageSender,
                pathConcatenatorSender
              )
            )
        }
    }
}
