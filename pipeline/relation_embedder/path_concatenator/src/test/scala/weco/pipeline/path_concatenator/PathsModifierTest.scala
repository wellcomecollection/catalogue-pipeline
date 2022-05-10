package weco.pipeline.path_concatenator

import akka.actor.ActorSystem
import com.sksamuel.elastic4s.Index
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.akka.fixtures.Akka
import weco.catalogue.internal_model.index.IndexFixtures
import weco.catalogue.internal_model.work.{CollectionPath, Work}
import weco.catalogue.internal_model.work.WorkState.Merged
import weco.catalogue.internal_model.work.generators.WorkGenerators
import weco.catalogue.internal_model.Implicits._
import weco.elasticsearch.test.fixtures.ElasticsearchFixtures

import scala.concurrent.ExecutionContext.Implicits.global



class PathsModifierTest extends AnyFunSpec
  with Matchers
  with ScalaFutures
  with IndexFixtures
  with ElasticsearchFixtures
  with Akka
  with WorkGenerators {

  private def modifier(index: Index)(implicit as: ActorSystem) =
    PathsModifier(new PathsService(
      elasticClient = elasticClient,
      index = index,
    ))

  private def work(path: String): Work.Visible[Merged] =
    mergedWork(createSourceIdentifierWith(value = path))
      .collectionPath(CollectionPath(path = path))
      .title(path)

  describe("PathsModifier") {
    it("does nothing if no parent or child path exists in the database") {
      withLocalMergedWorksIndex { index =>
        withActorSystem { implicit actorSystem =>
          whenReady(modifier(index).modifyPaths("terminal/path")) {
            _ shouldBe empty
          }
        }
      }
    }

    it("modifies the work with the given path") {
      val works: List[Work[Merged]] = List(
        work(path = "grandparent/parent"),
        work(path = "parent/child")
      )
      withLocalMergedWorksIndex { index =>
        insertIntoElasticsearch(index, works: _*)
        withActorSystem { implicit actorSystem =>
          whenReady(modifier(index).modifyPaths("parent/child")) { resultWorks =>
            resultWorks.head.data.collectionPath.get.path shouldBe "grandparent/parent/child"
            resultWorks.length shouldBe 1
          }
        }
      }
    }

    it("modifies the children of the work with the given path") {
      val works: List[Work[Merged]] = List(
        work(path = "grandparent/parent"),
        work(path = "parent/child")
      )
      withLocalMergedWorksIndex { index =>
        insertIntoElasticsearch(index, works: _*)
        withActorSystem { implicit actorSystem =>
          whenReady(modifier(index).modifyPaths("grandparent/parent")) { resultWorks =>
            resultWorks.head.data.collectionPath.get.path shouldBe "grandparent/parent/child"
            resultWorks.length shouldBe 1
          }
        }
      }
    }

    it("modifies both the work itself and its children") {
      val works: List[Work[Merged]] = List(
        work(path = "grandparent/parent"),
        work(path = "parent/child"),
        work(path = "child/grandchild")
      )
      withLocalMergedWorksIndex { index =>
        insertIntoElasticsearch(index, works: _*)
        withActorSystem { implicit actorSystem =>
          whenReady(modifier(index).modifyPaths("parent/child")) { resultWorks =>
            resultWorks map {
              resultWork => resultWork.data.collectionPath.get.path
            } should contain theSameElementsAs List(
              "grandparent/parent/child",
              "grandparent/parent/child/grandchild"
            )
          }
        }
      }
    }
  }


}
