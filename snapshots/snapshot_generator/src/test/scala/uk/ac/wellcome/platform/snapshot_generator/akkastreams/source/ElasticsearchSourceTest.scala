package uk.ac.wellcome.platform.snapshot_generator.akkastreams.source

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{Sink, Source}
import com.sksamuel.elastic4s.Index
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.akka.fixtures.Akka
import uk.ac.wellcome.fixtures.TestWith
import uk.ac.wellcome.models.Implicits._
import uk.ac.wellcome.models.index.IndexFixtures
import uk.ac.wellcome.models.work.generators.WorkGenerators
import uk.ac.wellcome.platform.snapshot_generator.models.SnapshotGeneratorConfig
import weco.catalogue.internal_model.work.{Work, WorkState}

class ElasticsearchSourceTest
    extends AnyFunSpec
    with Matchers
    with Akka
    with IndexFixtures
    with WorkGenerators {

  it("outputs the entire content of the index") {
    withActorSystem { implicit actorSystem =>
      withLocalWorksIndex { index =>
        val works = indexedWorks(count = 10)
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
        val visibleWorks = indexedWorks(count = 10)
        val invisibleWorks = indexedWorks(count = 3).map { _.invisible() }

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
    testWith: TestWith[Source[Work[WorkState.Indexed], NotUsed], R])(
    implicit actorSystem: ActorSystem): R = {
    val source = ElasticsearchWorksSource(
      elasticClient = elasticClient,
      snapshotConfig = SnapshotGeneratorConfig(index = index)
    )
    testWith(source)
  }
}
