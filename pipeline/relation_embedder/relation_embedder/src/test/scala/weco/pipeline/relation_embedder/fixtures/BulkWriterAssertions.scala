package weco.pipeline.relation_embedder.fixtures

import org.apache.pekko.NotUsed
import org.apache.pekko.stream.Materializer
import org.apache.pekko.stream.scaladsl.{Sink, Source}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.Assertion
import weco.catalogue.internal_model.work.{Work, WorkState}
import weco.catalogue.internal_model.work.WorkState.Denormalised
import weco.pekko.fixtures.Pekko
import weco.pipeline.relation_embedder.BulkWriter

trait BulkWriterAssertions
    extends AnyFunSpec
    with Matchers
    with Pekko
    with RelationGenerators
    with ScalaFutures {

  protected def works(n: Int): List[Work[Denormalised]] =
    List.fill(n)(denormalisedWork())

  /** Write a list of Works using the given `writer`. then test what has just
    * happened in the given `assertion`
    */
  protected def assertWhenWritingCompleted(workList: List[Work[Denormalised]])(
    assertion: Seq[Seq[Work[Denormalised]]] => Assertion
  )(
    implicit writer: BulkWriter
  ): Assertion = {

    val worksSource: Source[Work[WorkState.Denormalised], NotUsed] =
      Source(workList)

    withMaterializer {
      implicit materializer: Materializer =>
        {
          val stream = worksSource
            .via(writer.writeWorksFlow)
            .runWith(Sink.seq)
          whenReady(stream)(assertion)
        }
    }
  }

  protected def assertResultsHaveLengths(
    workList: List[Work[Denormalised]],
    lengths: Seq[Int]
  )(implicit writer: BulkWriter): Assertion = {
    assertWhenWritingCompleted(workList) {
      result: Seq[Seq[Work[Denormalised]]] =>
        val actualLengths = result.map(_.length)
        actualLengths should contain theSameElementsAs lengths
    }
  }
}
