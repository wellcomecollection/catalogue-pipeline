package weco.pipeline.relation_embedder

import org.scalatest.Inspectors
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import weco.catalogue.internal_model.work.{Relations, Work, WorkState}
import org.scalatest.concurrent.TimeLimits
import weco.pipeline.relation_embedder.fixtures.BulkWriterAssertions

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class BulkWriterTest
    extends BulkWriterAssertions
    with Inspectors
    with TimeLimits {

  // what the writer does to actually write things out is not the concern of these tests
  // the writer bundles the input stream up into chunks that the recipient
  // of the write can handle. Having written them, it emits those chunks
  // The NoOp writer does nothing but the chunking.
  class BulkNoOpWriter(
    override val maxBatchWeight: Int
  ) extends BulkWriter {
    override protected def writeWorks(
      works: Seq[Work[WorkState.Denormalised]]
    ): Future[Seq[Work[WorkState.Denormalised]]] =
      Future(works)
  }

  it("bundles a series of works into a sequences of size `weight`") {
    implicit val writer: BulkWriter = new BulkNoOpWriter(2)
    assertResultsHaveLengths(works(20), Seq.fill(10)(2))
  }

  it("emits and completes promptly when it cannot make a full bulk batch") {
    implicit val writer: BulkWriter = new BulkNoOpWriter(2)
    failAfter(1.second) {
      assertResultsHaveLengths(works(5), Seq(2, 2, 1))
    }
  }

  it("considers 20 relations to be equivalent to a whole work") {
    implicit val writer: BulkWriter = new BulkNoOpWriter(4)
    val unrelatedWorks = works(2)
    // A work with 20 relations is worth two with none, the 20 relations and the work itself.
    val twoWeightWork = denormalisedWork(relations =
      new Relations(siblingsPreceding = relations(20))
    )
    val threeWeightWork = denormalisedWork(relations =
      new Relations(siblingsPreceding = relations(40))
    )

    val fourWeightWork = denormalisedWork(relations =
      new Relations(siblingsPreceding = relations(60))
    )

    assertResultsHaveLengths(
      unrelatedWorks :+ twoWeightWork :+ fourWeightWork :+ threeWeightWork :+ denormalisedWork(),
      Seq(3, 1, 2)
    )
  }

  it("considers roughly 20 relations to equal 20 when calculating weight") {
    implicit val writer: BulkWriter = new BulkNoOpWriter(4)
    val unrelatedWorks = works(2)
    // A work with 20 relations is worth two with none, the 20 relations and the work itself.
    val twoishWeightWork = denormalisedWork(relations =
      new Relations(siblingsPreceding = relations(18))
    )
    val threeishWeightWork = denormalisedWork(relations =
      new Relations(siblingsPreceding = relations(41))
    )

    val fourishWeightWork = denormalisedWork(relations =
      new Relations(siblingsPreceding = relations(64))
    )

    assertResultsHaveLengths(
      unrelatedWorks :+ twoishWeightWork :+ fourishWeightWork :+ threeishWeightWork :+ denormalisedWork(),
      Seq(3, 1, 2)
    )
  }
}
