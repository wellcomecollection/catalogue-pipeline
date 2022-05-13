package weco.pipeline.path_concatenator

import com.sksamuel.elastic4s.Index
import org.scalatest.Assertion
import org.scalatest.concurrent.{Eventually, IntegrationPatience}
import org.scalatest.funspec.AnyFunSpec
import weco.akka.fixtures.Akka
import weco.catalogue.internal_model.index.IndexFixtures
import weco.catalogue.internal_model.work.WorkState.Merged
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.catalogue.internal_model.work.{CollectionPath, Work}
import weco.elasticsearch.test.fixtures.ElasticsearchFixtures
import weco.fixtures.TestWith
import weco.messaging.fixtures.SQS.QueuePair
import weco.messaging.memory.MemoryMessageSender
import weco.messaging.sns.NotificationMessage
import weco.pipeline_storage.fixtures.PipelineStorageStreamFixtures
import weco.pipeline_storage.memory.MemoryIndexer
import weco.catalogue.internal_model.Implicits._

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

/**
  * Tests covering the Path Concatenator Worker Service, which
  * responds to SQS messages to change records in a given Elasticsearch database
  *
  * These tests require running instances of
  * - ElasticSearch
  * - Localstack
  *   - docker run --env SERVICES=sqs -p4566:4566 localstack/localstack:0.12.20
  */
class PathConcatenatorWorkerServiceTest
    extends AnyFunSpec
    with WorkGenerators
    with PipelineStorageStreamFixtures
    with Eventually
    with IntegrationPatience
    with Akka
    with ElasticsearchFixtures
    with IndexFixtures {

  it(
    "updates a work and its children, sending all their paths to the downstream queue") {
    val works = List(
      work("a/b"),
      work("b/c"),
      work("c/d"),
    )

    withWorkerService(works) {
      case (QueuePair(queue, dlq), index, downstreamMessageSender) =>
        sendNotificationToSQS(queue = queue, body = "b/c")
        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
          assertIndexContainsPaths(
            index,
            Map(
              works(1).id -> "a/b/c",
              works(2).id -> "a/b/c/d"
            ))
          assertQueueContainsPaths(
            downstreamMessageSender,
            List("b/c", "a/b/c", "a/b/c/d"))
        }
    }
  }
  private def work(path: String): Work.Visible[Merged] =
    mergedWork(createSourceIdentifierWith(value = path))
      .collectionPath(CollectionPath(path = path))
      .title(path)

  private def assertIndexContainsPaths(
    inMemoryIndex: mutable.Map[String, Work[Merged]],
    pathMap: Map[String, String]) =
    inMemoryIndex map {
      case (workId, work) =>
        (workId, work.data.collectionPath.get.path)
    } should contain theSameElementsAs pathMap

  private def assertQueueContainsPaths(sender: MemoryMessageSender,
                                       expectedPaths: List[String]) =
    sender.messages.map(_.body) should contain theSameElementsAs expectedPaths

  private def storeWorks(index: Index, works: List[Work[Merged]]): Assertion =
    insertIntoElasticsearch(index, works: _*)

  private def withWorkerService[R](
    works: List[Work[Merged]]
  )(
    testWith: TestWith[
      (QueuePair, mutable.Map[String, Work[Merged]], MemoryMessageSender),
      R
    ]
  ): R =
    withLocalMergedWorksIndex { mergedIndex =>
      storeWorks(mergedIndex, works)
      withLocalSqsQueuePair(visibilityTimeout = 5.seconds) { queuePair =>
        withActorSystem { implicit actorSystem =>
          withSQSStream[NotificationMessage, R](queuePair.queue) { sqsStream =>
            val messageSender = new MemoryMessageSender
            val pathsService = new PathsService(
              elasticClient = elasticClient,
              index = mergedIndex)
            val outputIndex =
              mutable.Map.empty[String, Work[Merged]]
            val workerService = new PathConcatenatorWorkerService[String](
              sqsStream = sqsStream,
              msgSender = messageSender,
              workIndexer = new MemoryIndexer(outputIndex),
              pathsModifier = PathsModifier(pathsService),
            )
            workerService.run()
            testWith((queuePair, outputIndex, messageSender))
          }
        }
      }
    }

}
