package uk.ac.wellcome.platform.snapshot_generator.flow

import akka.stream.scaladsl.{Sink, Source}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.display.models.{DisplayWork, WorksIncludes}
import uk.ac.wellcome.models.work.generators.LegacyWorkGenerators

class IdentifiedWorkToVisibleDisplayWorkFlowTest
    extends AnyFunSpec
    with Matchers
    with Akka
    with ScalaFutures
    with IntegrationPatience
    with LegacyWorkGenerators {

  it("creates DisplayWorks from IdentifiedWorks") {
    withMaterializer { implicit materializer =>
      val flow = IdentifiedWorkToVisibleDisplayWork(
        toDisplayWork = DisplayWork.apply(_, WorksIncludes.includeAll()))

      val works = createIdentifiedWorks(count = 3).toList

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
