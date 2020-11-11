package uk.ac.wellcome.relation_embedder

import com.sksamuel.elastic4s.Index
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.messaging.fixtures.SQS
import uk.ac.wellcome.messaging.fixtures.SQS.QueuePair
import uk.ac.wellcome.messaging.memory.MemoryMessageSender
import uk.ac.wellcome.messaging.sns.NotificationMessage
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal.WorkState.{Denormalised, Merged}
import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.monitoring.memory.MemoryMetrics
import uk.ac.wellcome.pipeline_storage.{ElasticRetriever, MemoryIndexer}

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class RelationEmbedderWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with SQS
    with Akka
    with Eventually
    with ElasticsearchFixtures
    with WorkGenerators {

  def work(path: String) =
    mergedWork(createSourceIdentifierWith(value = path))
      .collectionPath(CollectionPath(path = path))
      .title(path)

  def storeWorks(index: Index, works: List[Work[Merged]] = works): Assertion =
    insertIntoElasticsearch(index, works: _*)

  val workA = work("a")
  val work1 = work("a/1")
  val workB = work("a/1/b")
  val work2 = work("a/2")
  val workC = work("a/2/c")
  val workD = work("a/2/d")
  val workE = work("a/2/d/e")

  val relationsA = Relations(
    children = List(Relation(work1, 1), Relation(work2, 1)))
  val relations1 = Relations(
    ancestors = List(Relation(workA, 0)),
    children = List(Relation(workB, 2)),
    siblingsSucceeding = List(Relation(work2, 1)))
  val relationsB = Relations(
    ancestors = List(Relation(workA, 0), Relation(work1, 1)))
  val relations2 = Relations(
    ancestors = List(Relation(workA, 0)),
    children = List(Relation(workC, 2), Relation(workD, 2)),
    siblingsPreceding = List(Relation(work1, 1)))
  val relationsC = Relations(
    ancestors = List(Relation(workA, 0), Relation(work2, 1)),
    siblingsSucceeding = List(Relation(workD, 2)))
  val relationsD = Relations(
    ancestors = List(Relation(workA, 0), Relation(work2, 1)),
    children = List(Relation(workE, 3)),
    siblingsPreceding = List(Relation(workC, 2)))
  val relationsE = Relations(ancestors =
    List(Relation(workA, 0), Relation(work2, 1), Relation(workD, 2)))

  val works =
    List(workA, workB, workC, workD, workE, work2, work1)

  def relations(index: mutable.Map[String, Work[Denormalised]])
    : Map[String, Relations[DataState.Unidentified]] =
    index.map { case (key, work) => key -> work.state.relations }.toMap

  it("denormalises a leaf work and its immediate parent") {
    withWorkerService() {
      case (QueuePair(queue, dlq), index, msgSender) =>
        sendNotificationToSQS(queue = queue, body = workE.id)
        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
          msgSender.messages.map(_.body).toSet shouldBe Set(workD.id, workE.id)
          relations(index) shouldBe Map(
            workD.id -> relationsD,
            workE.id -> relationsE,
          )
        }
    }
  }

  it("denormalises the whole tree when given the root") {
    withWorkerService() {
      case (QueuePair(queue, dlq), index, msgSender) =>
        sendNotificationToSQS(queue = queue, body = workA.id)
        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
        msgSender.messages.map(_.body).toSet shouldBe works.map(_.id).toSet
        relations(index) shouldBe Map(
          workA.id -> relationsA,
          work1.id -> relations1,
          workB.id -> relationsB,
          work2.id -> relations2,
          workC.id -> relationsC,
          workD.id -> relationsD,
          workE.id -> relationsE,
        )
    }
  }

  def withWorkerService[R](works: List[Work[Merged]] = works)(
    testWith: TestWith[(QueuePair,
                        mutable.Map[String, Work[Denormalised]],
                        MemoryMessageSender),
                       R]): R =
    withLocalMergedWorksIndex { mergedIndex =>
      storeWorks(mergedIndex, works)
      withLocalSqsQueuePair() { queuePair =>
        withActorSystem { implicit actorSystem =>
          withSQSStream[NotificationMessage, R](
            queuePair.queue,
            new MemoryMetrics) { sqsStream =>
            val messageSender = new MemoryMessageSender
            val denormalisedIndex =
              mutable.Map.empty[String, Work[Denormalised]]
            val workerService = new RelationEmbedderWorkerService[String](
              sqsStream = sqsStream,
              msgSender = messageSender,
              workRetriever = new ElasticRetriever(elasticClient, mergedIndex),
              workIndexer = new MemoryIndexer(denormalisedIndex),
              relationsService =
                new PathQueryRelationsService(elasticClient, mergedIndex, 10),
              indexFlushInterval = 1 milliseconds,
            )
            workerService.run()
            testWith((queuePair, denormalisedIndex, messageSender))
          }
        }
      }
    }
}
