package uk.ac.wellcome.platform.snapshot_generator.source

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import com.sksamuel.elastic4s.Index
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.elasticsearch.test.fixtures.ElasticsearchFixtures
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.models.work.internal._

class ElasticsearchSourceTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with IntegrationPatience
    with Akka
    with ElasticsearchFixtures
    with WorkGenerators {

  it("outputs the entire content of the index") {
    withActorSystem { implicit actorSystem =>
      withLocalWorksIndex { index =>
        val works = (1 to 10).map { _ =>
          identifiedWork()
        }
        insertIntoElasticsearch(index, works: _*)

        withSource(index) { source =>
          val future = source.runWith(Sink.seq)

          whenReady(future) { result =>
            result should contain theSameElementsAs works
          }
        }
      }
    }
  }

  it("filters non visible works") {
    withActorSystem { implicit actorSystem =>
      withLocalWorksIndex { index =>
        val visibleWorks = (1 to 10).map { _ =>
          identifiedWork()
        }
        val invisibleWorks = (1 to 3).map { _ =>
          identifiedWork().invisible()
        }

        val works = visibleWorks ++ invisibleWorks
        insertIntoElasticsearch(index, works: _*)

        withSource(index) { source =>
          val future = source.runWith(Sink.seq)

          whenReady(future) { result =>
            result should contain theSameElementsAs visibleWorks
          }
        }
      }
    }
  }

  private def withSource[R](index: Index)(
    testWith: TestWith[Source[Work[WorkState.Identified], NotUsed], R])(
    implicit actorSystem: ActorSystem): R = {
    val source = ElasticsearchWorksSource(
      elasticClient = elasticClient,
      index = index
    )
    testWith(source)
  }
}
