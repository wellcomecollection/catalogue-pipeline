package weco.pipeline.relation_embedder

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.pekko.fixtures.Pekko
import weco.pipeline.relation_embedder.fixtures.{BulkWriterAssertions, SampleWorkTree}
import org.apache.pekko.stream.Materializer
import weco.catalogue.internal_model.work.{Availability, Relations, Work}
import weco.catalogue.internal_model.work.WorkState.{Denormalised, Merged}
import weco.fixtures.TestWith
import weco.lambda.helpers.MemoryDownstream
import weco.pipeline.relation_embedder.models.Batch
import weco.pipeline.relation_embedder.models.Selector.{Descendents, Node, Tree}
import weco.pipeline_storage.memory.MemoryIndexer

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global

class BatchProcessorTest
    extends AnyFunSpec
    with Matchers
    with SampleWorkTree
    with Pekko
    with BulkWriterAssertions
    with MemoryDownstream {

  protected def withProcessedBatch[R](
    workList: List[Work[Merged]],
    batch: Batch
  )(
    testWith: TestWith[
      (Seq[String], mutable.Map[String, Work[Denormalised]]),
      R
    ]
  ): R = {
    val denormalisedIndex =
      mutable.Map.empty[String, Work[Denormalised]]

    implicit val bulkWriter: BulkWriter = new BulkIndexWriter(
      workIndexer = new MemoryIndexer(denormalisedIndex),
      maxBatchWeight = 10
    )
    withUpstreamIndex(workList) {
      mergedIndex =>
        val relationsService = new PathQueryRelationsService(
          elasticClient,
          mergedIndex,
          10
        )
        withMaterializer {
          implicit materializer: Materializer =>
            val downstream = new MemorySNSDownstream
            val processor = new BatchProcessor(
              relationsService = relationsService,
              bulkWriter = bulkWriter,
              downstream = downstream
            )

            whenReady(processor(batch)) {
              _ =>
                testWith(
                  (
                    downstream.msgSender.messages.map(_.body),
                    denormalisedIndex
                  )
                )
            }

        }
    }
  }

  describe("given a batch containing a list of selectors") {
    withProcessedBatch(
      works,
      Batch(
        rootPath = "a",
        selectors = List(Node("a/2"), Descendents("a/2"))
      )
    ) {
      case (
            messages: Seq[String],
            downstreamIndex: mutable.Map[String, Work[Denormalised]]
          ) =>
        it(
          "notifies downstream that all Works matching the Selectors have changed"
        ) {

          messages should contain theSameElementsAs Seq(
            work2.id,
            workC.id,
            workD.id,
            workE.id
          )
        }

        it("populates the relations for each affected Work") {

          relations(downstreamIndex) shouldBe Map(
            work2.id -> relations2,
            workC.id -> relationsC,
            workD.id -> relationsD,
            workE.id -> relationsE
          )
        }

        it("preserves any availability values on writing") {
          availabilities(downstreamIndex) shouldBe Map(
            work2.id -> Set.empty,
            workC.id -> Set.empty,
            workD.id -> Set(Availability.Online),
            workE.id -> Set.empty
          )
        }
    }
  }

  describe("given a batch containing the Whole Tree selector") {
    withProcessedBatch(
      works,
      Batch(rootPath = "a", selectors = List(Tree("a")))
    ) {
      case (
            messages: Seq[String],
            downstreamIndex: mutable.Map[String, Work[Denormalised]]
          ) =>
        it(
          "notifies downstream that all Works in the tree have changed"
        ) {
          messages should contain theSameElementsAs works.map(_.id)
        }
        it("populates the relations for every Work in the tree") {
          relations(downstreamIndex) shouldBe Map(
            workA.id -> relationsA,
            work1.id -> relations1,
            workB.id -> relationsB,
            work2.id -> relations2,
            workC.id -> relationsC,
            workD.id -> relationsD,
            workE.id -> relationsE
          )
        }
        it("preserves any availability values on writing") {
          availabilities(downstreamIndex) shouldBe Map(
            workA.id -> Set.empty,
            work1.id -> Set.empty,
            workB.id -> Set.empty,
            work2.id -> Set.empty,
            workC.id -> Set.empty,
            workD.id -> Set(Availability.Online),
            workE.id -> Set.empty
          )
        }
    }
  }

  describe("given a tree containing an invisible Work") {
    val invisibleWork = work("a/2/invisible").invisible()

    withProcessedBatch(
      invisibleWork :: works,
      Batch(rootPath = "a", selectors = List(Tree("a")))
    ) {
      case (
            messages: Seq[String],
            downstreamIndex: mutable.Map[String, Work[Denormalised]]
          ) =>
        it(
          "includes the invisible Work in the downstream notifications"
        ) {
          messages should contain theSameElementsAs (invisibleWork :: works)
            .map(_.id)
        }
        it("populates relations, but not on the invisible work") {
          relations(downstreamIndex) shouldBe Map(
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
        it("preserves any availability values on writing") {
          availabilities(downstreamIndex) shouldBe Map(
            workA.id -> Set.empty,
            work1.id -> Set.empty,
            workB.id -> Set.empty,
            work2.id -> Set.empty,
            workC.id -> Set.empty,
            workD.id -> Set(Availability.Online),
            workE.id -> Set.empty,
            invisibleWork.id -> Set.empty
          )
        }
    }
  }

}
