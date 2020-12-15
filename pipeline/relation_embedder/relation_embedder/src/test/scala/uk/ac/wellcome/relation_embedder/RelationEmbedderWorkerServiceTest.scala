package uk.ac.wellcome.relation_embedder

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import com.sksamuel.elastic4s.Index
import org.scalatest.Assertion
import org.scalatest.concurrent.Eventually
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import akka.NotUsed
import akka.stream.scaladsl.Source
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
import uk.ac.wellcome.pipeline_storage.MemoryIndexer
import uk.ac.wellcome.json.JsonUtil._

class RelationEmbedderWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with SQS
    with Akka
    with Eventually
    with ElasticsearchFixtures
    with WorkGenerators {

  def work(path: String) =
    mergedWork(
      sourceIdentifier = createSourceIdentifierWith(value = path),
      canonicalId = path
    ).collectionPath(CollectionPath(path = path))
      .title(path)

  def storeWorks(index: Index, works: List[Work[Merged]] = works): Assertion =
    insertIntoElasticsearch(index, works: _*)

  /** The following tests use works within this tree:
    *
    * a
    * |---
    * |  |
    * 1  2
    * |  |---
    * |  |  |
    * b  c  d
    *    |
    *    |
    *    e
    */
  val workA = work("a")
  val work1 = work("a/1")
  val workB = work("a/1/b")
  val work2 = work("a/2")
  val workC = work("a/2/c")
  val workD = work("a/2/d")
  val workE = work("a/2/d/e")

  val relA = Relation(workA, depth = 0, numChildren = 2, numDescendents = 6)
  val rel1 = Relation(work1, depth = 1, numChildren = 1, numDescendents = 1)
  val relB = Relation(workB, depth = 2, numChildren = 0, numDescendents = 0)
  val rel2 = Relation(work2, depth = 1, numChildren = 2, numDescendents = 3)
  val relC = Relation(workC, depth = 2, numChildren = 0, numDescendents = 0)
  val relD = Relation(workD, depth = 2, numChildren = 1, numDescendents = 1)
  val relE = Relation(workE, depth = 3, numChildren = 0, numDescendents = 0)

  val relationsA = Relations(children = List(rel1, rel2))
  val relations1 = Relations(
    ancestors = List(relA),
    children = List(relB),
    siblingsSucceeding = List(rel2)
  )
  val relationsB = Relations(ancestors = List(relA, rel1))
  val relations2 = Relations(
    ancestors = List(relA),
    children = List(relC, relD),
    siblingsPreceding = List(rel1))
  val relationsC =
    Relations(ancestors = List(relA, rel2), siblingsSucceeding = List(relD))
  val relationsD = Relations(
    ancestors = List(relA, rel2),
    children = List(relE),
    siblingsPreceding = List(relC))
  val relationsE = Relations(ancestors = List(relA, rel2, relD))

  val works =
    List(workA, workB, workC, workD, workE, work2, work1)

  def relations(
    index: mutable.Map[String, Work[Denormalised]]): Map[String, Relations] =
    index.map { case (key, work) => key -> work.state.relations }.toMap

  it("denormalises a batch containing a list of selectors") {
    withWorkerService() {
      case (QueuePair(queue, dlq), index, msgSender) =>
        import Selector._
        val batch = Batch(
          rootPath = "a",
          selectors = List(Node("a/2"), Descendents("a/2")))
        sendNotificationToSQS(queue = queue, message = batch)
        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
        msgSender.messages.map(_.body).toSet shouldBe Set(
          work2.id,
          workC.id,
          workD.id,
          workE.id
        )
        relations(index) shouldBe Map(
          work2.id -> relations2,
          workC.id -> relationsC,
          workD.id -> relationsD,
          workE.id -> relationsE,
        )
    }
  }

  it("denormalises a batch containing the whole tree") {
    withWorkerService() {
      case (QueuePair(queue, dlq), index, msgSender) =>
        import Selector._
        val batch = Batch(rootPath = "a", selectors = List(Tree("a")))
        sendNotificationToSQS(queue = queue, message = batch)
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

  it("denormalises a batch containing invisible works") {
    val invisibleWork = work("a/2/invisible").invisible()
    withWorkerService(invisibleWork :: works) {
      case (QueuePair(queue, dlq), index, msgSender) =>
        import Selector._
        val batch = Batch(rootPath = "a", selectors = List(Tree("a")))
        sendNotificationToSQS(queue = queue, message = batch)
        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
        msgSender.messages.map(_.body).toSet shouldBe (invisibleWork :: works)
          .map(_.id)
          .toSet
        relations(index) shouldBe Map(
          workA.id -> relationsA,
          work1.id -> relations1,
          workB.id -> relationsB,
          work2.id -> relations2,
          workC.id -> relationsC,
          workD.id -> relationsD,
          workE.id -> relationsE,
          invisibleWork.id -> Relations.none
        )
    }
  }

  it("puts failed messages onto the DLQ") {
    withWorkerService(fails = true) {
      case (QueuePair(queue, dlq), index, msgSender) =>
        import Selector._
        val batch = Batch(rootPath = "a", selectors = List(Tree("a")))
        sendNotificationToSQS(queue = queue, message = batch)
        eventually {
          assertQueueEmpty(queue)
        }
        assertQueueHasSize(dlq, size = 1)
        msgSender.messages.map(_.body).toSet shouldBe Set()
    }
  }

  def withWorkerService[R](works: List[Work[Merged]] = works,
                           fails: Boolean = false)(
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
            val relationsService =
              if (fails) FailingRelationsService
              else
                new PathQueryRelationsService(elasticClient, mergedIndex, 10)
            val workerService = new RelationEmbedderWorkerService[String](
              sqsStream = sqsStream,
              msgSender = messageSender,
              workIndexer = new MemoryIndexer(denormalisedIndex),
              relationsService = relationsService,
              indexFlushInterval = 1 milliseconds,
            )
            workerService.run()
            testWith((queuePair, denormalisedIndex, messageSender))
          }
        }
      }
    }

  object FailingRelationsService extends RelationsService {
    def getAffectedWorks(batch: Batch): Source[Work[Merged], NotUsed] =
      Source.single(()).map[Work[Merged]](throw new Exception("Failing"))

    def getRelationTree(batch: Batch): Source[RelationWork, NotUsed] =
      Source.single(()).map[RelationWork](throw new Exception("Failing"))
  }
}
