package weco.pipeline.relation_embedder

import com.sksamuel.elastic4s.Index
import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.Source
import org.apache.commons.io.IOUtils
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.time.{Seconds, Span}
import weco.pekko.fixtures.Pekko
import weco.fixtures.TestWith
import weco.json.JsonUtil._
import weco.messaging.fixtures.SQS
import weco.messaging.fixtures.SQS.QueuePair
import weco.messaging.sns.NotificationMessage
import weco.catalogue.internal_model.work.WorkState.{Denormalised, Merged}
import weco.catalogue.internal_model.work._
import weco.lambda.helpers.MemoryDownstream
import weco.pipeline.relation_embedder.fixtures.SampleWorkTree
import weco.pipeline.relation_embedder.models._
import weco.pipeline_storage.memory.MemoryIndexer

import java.nio.charset.StandardCharsets

class RelationEmbedderWorkerServiceTest
    extends AnyFunSpec
    with Matchers
    with SQS
    with Pekko
    with SampleWorkTree
    with MemoryDownstream {

  // This test is a duplicate of one in BatchProcessorTest,
  // wrapped in the furniture of the Worker Service.
  // The results should remain the same. This demonstrates that
  // the SQS Messages are appropriately passed on to a correctly
  // configured BatchProcessor.
  it("denormalises a batch containing a list of selectors") {
    withWorkerService() {
      case (QueuePair(queue, dlq), index, downstream) =>
        import Selector._
        val batch = Batch(
          rootPath = "a",
          selectors = List(Node("a/2"), Descendents("a/2"))
        )
        sendNotificationToSQS(queue = queue, message = batch)
        eventually {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }
        downstream.msgSender.messages.map(_.body).toSet shouldBe Set(
          work2.id,
          workC.id,
          workD.id,
          workE.id
        )
        relations(index) shouldBe Map(
          work2.id -> relations2,
          workC.id -> relationsC,
          workD.id -> relationsD,
          workE.id -> relationsE
        )
        availabilities(index) shouldBe Map(
          work2.id -> Set.empty,
          workC.id -> Set.empty,
          workD.id -> Set(Availability.Online),
          workE.id -> Set.empty
        )
    }
  }

  it("puts failed messages onto the DLQ") {
    withWorkerService(fails = true, visibilityTimeout = 1 second) {
      case (QueuePair(queue, dlq), _, downstream) =>
        import Selector._
        val batch = Batch(rootPath = "a", selectors = List(Tree("a")))
        sendNotificationToSQS(queue = queue, message = batch)
        eventually {
          assertQueueEmpty(queue)
        }
        assertQueueHasSize(dlq, size = 1)
        downstream.msgSender.messages.map(_.body).toSet shouldBe Set()
    }
  }

  // This is a real set of nearly 7000 paths from SAFPA.  This test is less focused on
  // the exact result, more that it returns in a reasonable time.
  //
  // In particular, the relation embedder has to handle large batches, but most of our
  // other tests use small samples.  This helps us catch accidentally introduced
  // complexity errors before we deploy new code.
  it("handles a very large collection of works") {
    val paths = IOUtils
      .resourceToString("/paths.txt", StandardCharsets.UTF_8)
      .split("\n")

    val works = paths.map { work(_) }.toList

    val batch = Batch(
      rootPath = "SAFPA",
      selectors = List(
        Selector.Descendents("SAFPA/A/A14/1/48/2"),
        Selector.Children("SAFPA/A/A14/1/48"),
        Selector.Node("SAFPA/A/A14/1/48")
      )
    )

    withWorkerService(works, visibilityTimeout = 30.seconds) {
      case (QueuePair(queue, dlq), _, downstream) =>
        sendNotificationToSQS(queue = queue, message = batch)

        eventually(Timeout(Span(45, Seconds))) {
          assertQueueEmpty(queue)
          assertQueueEmpty(dlq)
        }

        downstream.msgSender.messages should have size 5
    }
  }

  def withWorkerService[R](
    works: List[Work[Merged]] = works,
    fails: Boolean = false,
    visibilityTimeout: Duration = 5.seconds
  )(
    testWith: TestWith[
      (QueuePair, mutable.Map[String, Work[Denormalised]], MemorySNSDownstream),
      R
    ]
  ): R = {
    withUpstreamIndex(works) {
      mergedIndex: Index =>
        withLocalSqsQueuePair(visibilityTimeout = visibilityTimeout) {
          queuePair =>
            withActorSystem {
              implicit actorSystem =>
                withSQSStream[NotificationMessage, R](queuePair.queue) {
                  sqsStream =>
                    val downstream = new MemorySNSDownstream
                    val denormalisedIndex =
                      mutable.Map.empty[String, Work[Denormalised]]
                    val relationsService =
                      if (fails) FailingRelationsService
                      else
                        new PathQueryRelationsService(
                          elasticClient,
                          mergedIndex,
                          10
                        )
                    val bulkWriter = new BulkIndexWriter(
                      workIndexer = new MemoryIndexer(denormalisedIndex),
                      maxBatchWeight = 100
                    )
                    val processor = new BatchProcessor(
                      downstream = downstream,
                      bulkWriter = bulkWriter,
                      relationsService = relationsService
                    )
                    val workerService =
                      new RelationEmbedderWorkerService[String](
                        sqsStream = sqsStream,
                        batchProcessor = processor
                      )
                    workerService.run()
                    testWith((queuePair, denormalisedIndex, downstream))
                }
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
