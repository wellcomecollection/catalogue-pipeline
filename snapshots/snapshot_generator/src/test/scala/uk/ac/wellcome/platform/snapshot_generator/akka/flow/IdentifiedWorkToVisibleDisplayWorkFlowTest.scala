package uk.ac.wellcome.platform.snapshot_generator.akka.flow

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

  it("creates DisplayWorks from IdentifiedWorks") {
    withMaterializer { implicit materializer =>
      val flow = IdentifiedWorkToVisibleDisplayWork(
        toDisplayWork = DisplayWork.apply(_, WorksIncludes.includeAll()))

      val works = identifiedWorks(count = 3)

      val eventualDisplayWorks = Source(works)
        .via(flow)
        .runWith(Sink.seq)

      whenReady(eventualDisplayWorks) { displayWorks =>
        val expectedDisplayWorks = works.map {
          DisplayWork(_, includes = WorksIncludes.includeAll())
        }
        displayWorks shouldBe expectedDisplayWorks
      }
    }
  }
}
