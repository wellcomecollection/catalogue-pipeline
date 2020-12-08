package uk.ac.wellcome.platform.router

import com.sksamuel.elastic4s.Index
import org.scalatest.concurrent.Eventually
import org.scalatest.funspec.AnyFunSpec
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal.WorkState.Denormalised
import uk.ac.wellcome.models.work.internal.{CollectionPath, Relations, Work}
import uk.ac.wellcome.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import uk.ac.wellcome.pipeline_storage.{
  ElasticRetriever,
  Indexer,
  MemoryIndexer
}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class RouterWorkerServiceTest
    extends AnyFunSpec
    with WorkGenerators
    with PipelineStorageStreamFixtures
    with ElasticsearchFixtures
    with Eventually {

  it("sends collectionPath to paths topic") {
    val work = identifiedWork().collectionPath(CollectionPath("a"))
    val indexer = new MemoryIndexer[Work[Denormalised]]()
    withWorkerService(indexer) {
      case (
          identifiedIndex,
          QueuePair(queue, dlq),
          worksMessageSender,
          pathsMessageSender) =>
        insertIntoElasticsearch(identifiedIndex, work)
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
    val work = identifiedWork()
    val indexer = new MemoryIndexer[Work[Denormalised]]()
    withWorkerService(indexer) {
      case (
          identifiedIndex,
          QueuePair(queue, dlq),
          worksMessageSender,
          pathsMessageSender) =>
        insertIntoElasticsearch(identifiedIndex, work)
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
      identifiedWork().collectionPath(CollectionPath("a/2")).invisible()

    val indexer = new MemoryIndexer[Work[Denormalised]]()
    withWorkerService(indexer) {
      case (
          identifiedIndex,
          QueuePair(queue, dlq),
          worksMessageSender,
          pathsMessageSender) =>
        insertIntoElasticsearch(identifiedIndex, work)
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
    "sends the message to the dlq and doesn't send anything on if elastic indexing fails") {
    val work = identifiedWork()
    val failingIndexer = new Indexer[Work[Denormalised]] {
      override def init(): Future[Unit] = Future.successful(())
      override def index(documents: Seq[Work[Denormalised]])
        : Future[Either[Seq[Work[Denormalised]], Seq[Work[Denormalised]]]] =
        Future.successful(Left(documents))
    }
    withWorkerService(failingIndexer) {
      case (
          identifiedIndex,
          QueuePair(queue, dlq),
          worksMessageSender,
          pathsMessageSender) =>
        insertIntoElasticsearch(identifiedIndex, work)
        sendNotificationToSQS(queue = queue, body = work.id)

        eventually {
          assertQueueEmpty(queue)
          assertQueueHasSize(dlq, 1)
          worksMessageSender.messages shouldBe empty
          pathsMessageSender.messages shouldBe empty
        }
    }
  }

  def withWorkerService[R](indexer: Indexer[Work[Denormalised]])(
    testWith: TestWith[(Index,
                        QueuePair,
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
          withLocalIdentifiedWorksIndex { index =>
            val service =
              new RouterWorkerService(
                pathsMsgSender = pathsMessageSender,
                workRetriever = new ElasticRetriever(elasticClient, index),
                pipelineStream = pipelineStream
              )
            service.run()
            testWith((index, q, worksMessageSender, pathsMessageSender))
          }
        }
    }
}
