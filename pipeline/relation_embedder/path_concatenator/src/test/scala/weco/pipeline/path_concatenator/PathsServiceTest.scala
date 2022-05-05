package weco.pipeline.path_concatenator

import akka.actor.ActorSystem
import akka.stream.scaladsl.Sink
import com.sksamuel.elastic4s.Index
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.index.{IndexFixtures}
import weco.catalogue.internal_model.work.{CollectionPath, Work}
import weco.catalogue.internal_model.work.WorkState.Merged
import weco.catalogue.internal_model.Implicits._
import weco.elasticsearch.test.fixtures.ElasticsearchFixtures
import weco.akka.fixtures.Akka
import scala.collection.immutable
import scala.concurrent.Future
import weco.catalogue.internal_model.work.generators.WorkGenerators

class PathsServiceTest
    extends AnyFunSpec
    with Matchers
    with IndexFixtures
    with ElasticsearchFixtures
    with Akka
    with WorkGenerators {

  private def work(path: String): Work.Visible[Merged] =
    mergedWork(createSourceIdentifierWith(value = path))
      .collectionPath(CollectionPath(path = path))
      .title(path)

  private def service(index: Index)(implicit as: ActorSystem) =
    new PathsService(
      elasticClient = elasticClient,
      index = index,
    )

  describe("The PathService parentPath getter") {
    it("retrieves the parent path corresponding to a child path") {
      val works: List[Work[Merged]] = List(
        work(path = "grandparent/parent"),
        work(path = "parent/child")
      )
      withLocalMergedWorksIndex { index =>
        insertIntoElasticsearch(index, works: _*)
        withActorSystem { implicit actorSystem =>
          whenReady(queryParentPath(service(index), childPath = "parent/child")) {
            _ shouldBe Vector("grandparent/parent")
          }
        }
      }
    }

    it("only fetches a complex parentPath, simple parents are ignored") {
      val works: List[Work[Merged]] = List(
        work(path = "parent"),
        work(path = "parent/child")
      )
      withLocalMergedWorksIndex { index =>
        insertIntoElasticsearch(index, works: _*)
        withActorSystem { implicit actorSystem =>
          whenReady(queryParentPath(service(index), childPath = "parent/child")) {
            _ shouldBe empty
          }
        }
      }
    }

    it("works with arbitrarily deep hierarchies") {
      val works: List[Work[Merged]] = List(
        work(path = "a/b/c/d/e"),
        work(path = "e/f/g/h/i")
      )
      withLocalMergedWorksIndex { index =>
        insertIntoElasticsearch(index, works: _*)
        withActorSystem { implicit actorSystem =>
          whenReady(queryParentPath(service(index), childPath = "e/f/g/h/i")) {
            _ shouldBe Vector("a/b/c/d/e")
          }
        }
      }
    }

    it("returns multiple matching parent paths") {
      // This represents a data error, but the pathService is not the
      // arbiter of what to do in this scenario, so it returns more than
      // one parent (TBC, it might be better to throw at this point)
      val works: List[Work[Merged]] = List(
        work(path = "grandmother/parent"),
        work(path = "grandfather/parent"),
        work(path = "parent/child")
      )
      withLocalMergedWorksIndex { index =>
        insertIntoElasticsearch(index, works: _*)
        withActorSystem { implicit actorSystem =>
          whenReady(queryParentPath(service(index), childPath = "parent/child")) {
            _ should contain theSameElementsAs Vector(
              "grandmother/parent",
              "grandfather/parent")
          }
        }
      }
    }

  }

  def queryParentPath(service: PathsService, childPath: String)(
    implicit as: ActorSystem): Future[immutable.Seq[String]] =
    service.getParentPath(childPath).runWith(Sink.seq[String])

}
