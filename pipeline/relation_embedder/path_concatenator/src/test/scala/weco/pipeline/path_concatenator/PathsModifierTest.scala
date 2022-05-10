package weco.pipeline.path_concatenator

import akka.actor.ActorSystem
import com.sksamuel.elastic4s.Index
import org.scalatest.Assertion
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

import java.lang.RuntimeException
import scala.concurrent.ExecutionContext.Implicits.global

class PathsModifierTest
    extends AnyFunSpec
    with Matchers
    with ScalaFutures
    with IndexFixtures
    with ElasticsearchFixtures
    with Akka
    with WorkGenerators {

  private def modifier(index: Index)(implicit as: ActorSystem) =
    PathsModifier(
      new PathsService(
        elasticClient = elasticClient,
        index = index,
      ))

  private def work(path: String): Work.Visible[Merged] =
    mergedWork(createSourceIdentifierWith(value = path))
      .collectionPath(CollectionPath(path = path))
      .title(path)

  private def withContext(works: List[Work[Merged]],
                          testFunction: PathsModifier => Assertion): Assertion =
    withLocalMergedWorksIndex { index =>
      insertIntoElasticsearch(index, works: _*)
      withActorSystem { implicit actorSystem =>
        testFunction(modifier(index))
      }
    }

  private def assertDoesNothing(worksInDB: List[Work[Merged]],
                                path: String): Assertion =
    withContext(worksInDB, { pathsModifier =>
      whenReady(pathsModifier.modifyPaths(path)) { resultWorks =>
        resultWorks shouldBe empty
      }
    })

  private def assertFails(worksInDB: List[Work[Merged]],
                          path: String): Assertion =
    withContext(worksInDB, { pathsModifier =>
      pathsModifier
        .modifyPaths(path)
        .failed
        .futureValue shouldBe a[RuntimeException]
    })

  describe("PathsModifier") {
    it("does nothing if no relevant works exist in the database") {
      assertDoesNothing(Nil, "any/path")
    }

    it("does nothing if no parent work exists in the database") {
      assertDoesNothing(
        List(
          work(path = "grandparent/parent")
        ),
        path = "grandparent/parent")
    }

    it("does nothing if the parent work is the root of its own path") {
      assertDoesNothing(
        List(
          work(path = "grandparent/parent"),
          work(path = "grandparent")
        ),
        path = "grandparent/parent"
      )
    }

    it("fails if more than one possible match exists for the requested path") {
      assertFails(
        List(
          work(path = "granny/parent"),
          work(path = "parent/child"),
          work(path = "parent/child")
        ),
        path = "parent/child"
      )
    }

    it("fails if more than one possible match exists for the parent path") {
      assertFails(
        List(
          work(path = "granny/parent"),
          work(path = "grandad/parent"),
          work(path = "parent/child")
        ),
        path = "parent/child"
      )
    }

    it("modifies the work with the given path") {
      val works: List[Work[Merged]] = List(
        work(path = "grandparent/parent"),
        work(path = "parent/child")
      )
      withContext(
        works, { pathsModifier =>
          whenReady(pathsModifier.modifyPaths("parent/child")) { resultWorks =>
            resultWorks.head.data.collectionPath.get.path shouldBe "grandparent/parent/child"
            resultWorks.length shouldBe 1
          }
        }
      )
    }

    it("modifies the children of the work with the given path") {
      val works: List[Work[Merged]] = List(
        work(path = "grandparent/parent"),
        work(path = "parent/child")
      )
      withContext(
        works, { pathsModifier =>
          whenReady(pathsModifier.modifyPaths("grandparent/parent")) {
            resultWorks =>
              resultWorks.head.data.collectionPath.get.path shouldBe "grandparent/parent/child"
              resultWorks.length shouldBe 1
          }
        }
      )
    }

    it("modifies both the work itself and its children") {
      val works: List[Work[Merged]] = List(
        work(path = "grandparent/parent"),
        work(path = "parent/child"),
        work(path = "child/grandchild")
      )
      withContext(
        works, { pathsModifier =>
          whenReady(pathsModifier.modifyPaths("parent/child")) { resultWorks =>
            resultWorks map { resultWork =>
              resultWork.data.collectionPath.get.path
            } should contain theSameElementsAs List(
              "grandparent/parent/child",
              "grandparent/parent/child/grandchild"
            )
          }
        }
      )
    }
  }
}
