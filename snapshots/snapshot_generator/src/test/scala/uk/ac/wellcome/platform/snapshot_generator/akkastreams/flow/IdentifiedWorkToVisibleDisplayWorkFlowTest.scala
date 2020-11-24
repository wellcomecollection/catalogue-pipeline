package uk.ac.wellcome.platform.snapshot_generator.akkastreams.flow

import akka.stream.scaladsl.{Sink, Source}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.display.models.{DisplayWork, WorksIncludes}
import uk.ac.wellcome.models.work.generators.WorkGenerators

class IdentifiedWorkToVisibleDisplayWorkFlowTest
    extends AnyFunSpec
    with Matchers
    with Akka
    with ScalaFutures
    with IntegrationPatience
    with WorkGenerators {

  it("creates DisplayWorks from IndexedWorks") {
    withMaterializer { implicit materializer =>
      val flow = IndexedWorkToVisibleDisplayWork(
        toDisplayWork = DisplayWork.apply(_, WorksIncludes.all))

      val works = indexedWorks(count = 3)

      val eventualDisplayWorks = Source(works)
        .via(flow)
        .runWith(Sink.seq)

      whenReady(eventualDisplayWorks) { displayWorks =>
        val expectedDisplayWorks = works.map {
          DisplayWork(_, includes = WorksIncludes.all)
        }
        displayWorks shouldBe expectedDisplayWorks
      }
    }
  }
}
