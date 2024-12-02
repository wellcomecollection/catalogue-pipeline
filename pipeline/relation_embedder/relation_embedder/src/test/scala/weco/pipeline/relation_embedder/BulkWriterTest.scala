package weco.pipeline.relation_embedder

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.scalatest.{Assertion, Inspectors}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.SpanSugar.convertIntToGrainOfTime
import weco.catalogue.internal_model.work.WorkState.Denormalised
import weco.catalogue.internal_model.work.{Relations, Work, WorkState}
import org.apache.pekko.stream.Materializer
import org.scalatest.concurrent.{ScalaFutures, TimeLimits}
import weco.pekko.fixtures.Pekko
import weco.pipeline.relation_embedder.fixtures.RelationGenerators

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

class BulkWriterTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with Pekko
    with Inspectors
    with RelationGenerators
    with TimeLimits {

  // what the writer does internally is not the concern of these tests
  // the writer bundles the input stream up into chunks that the recipient
  // of the write can handle. Having written them, it emits those chunks
  // The NoOp writer does nothing but the chunking.
  class BulkNoOpWriter(
    override val maxBatchWeight: Int,
    override val maxBatchWait: FiniteDuration
  ) extends BulkWriter {
    override protected def writeWorks(
      works: Seq[Work[WorkState.Denormalised]]
    ): Future[Seq[Work[WorkState.Denormalised]]] =
      Future(works)
  }

  def works(n: Int): List[Work[Denormalised]] =
    List.fill(n)(denormalisedWork())

  private def assertResultsHaveLengths(
    workList: List[Work[Denormalised]],
    lengths: Seq[Int]
  )(implicit writer: BulkWriter): Assertion = {

    val worksSource: Source[Work[WorkState.Denormalised], NotUsed] =
      Source(workList)

    withMaterializer {
      implicit materializer: Materializer =>
        {
          val stream = worksSource
            .via(writer.writeWorksFlow)
            .runWith(Sink.seq)
          whenReady(stream) {
            result: immutable.Seq[Seq[Work[Denormalised]]] =>
              val actualLengths = result.map(_.length)
              actualLengths should contain theSameElementsAs lengths
          }
        }
    }
  }

  it("bundles a series of works into a sequences of size `weight`") {
    implicit val writer: BulkWriter = new BulkNoOpWriter(2, 1.millisecond)
    assertResultsHaveLengths(works(20), Seq.fill(10)(2))
  }

  it("emits and completes promptly when it cannot make a full bulk batch") {
    implicit val writer: BulkWriter = new BulkNoOpWriter(2, 1.hour)
    failAfter(1.second) {
      assertResultsHaveLengths(works(5), Seq(2, 2, 1))
    }
  }

  it("considers 20 relations to be equivalent to a whole work") {
    implicit val writer: BulkWriter = new BulkNoOpWriter(4, 1.hour)
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
    implicit val writer: BulkWriter = new BulkNoOpWriter(4, 1.hour)
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
