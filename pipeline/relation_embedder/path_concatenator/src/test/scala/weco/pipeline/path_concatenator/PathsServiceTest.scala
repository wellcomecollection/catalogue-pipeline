package weco.pipeline.path_concatenator

import com.sksamuel.elastic4s.Index
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.index.IndexFixtures
import weco.catalogue.internal_model.work.{CollectionPath, Work}
import weco.catalogue.internal_model.work.WorkState.Merged
import weco.catalogue.internal_model.Implicits._
import weco.elasticsearch.test.fixtures.ElasticsearchFixtures
import weco.akka.fixtures.Akka

import weco.catalogue.internal_model.work.generators.WorkGenerators
import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Tests covering the PathsService, which fetches data from Elasticsearch
  * in order to support the PathConcatenator.
  *
  * These tests require a running ElasticSearch Instance.
  */
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

  private def service(index: Index) =
    new PathsService(
      elasticClient = elasticClient,
      index = index,
    )

  describe("The PathService parentPath getter") {
    it("retrieves the parent path corresponding to a child path") {
      // The parent of a path is one whose leaf node matches the root of the child
      val works: List[Work[Merged]] = List(
        work(path = "grandparent/parent"),
        work(path = "parent/child")
      )
      withLocalMergedWorksIndex { index =>
        insertIntoElasticsearch(index, works: _*)
        whenReady(service(index).getParentPath("parent/child")) {
          _ shouldBe Some("grandparent/parent")
        }
      }
    }

    it("only fetches a complex parentPath, simple parents are ignored") {
      // When the parent path consists of a single node, there is nothing to do,
      // because the point of this is to expand the "root" of the child with
      // the path to that node from the actual root. In this case, they would be
      // the same node.
      val works: List[Work[Merged]] = List(
        work(path = "parent"),
        work(path = "parent/child")
      )
      withLocalMergedWorksIndex { index =>
        insertIntoElasticsearch(index, works: _*)
        whenReady(service(index).getParentPath("parent/child")) {
          _ shouldBe empty
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
        whenReady(service(index).getParentPath("e/f/g/h/i")) {
          _ shouldBe Some("a/b/c/d/e")
        }
      }
    }

    it("throws an exception if there are multiple matching parent paths") {
      val works: List[Work[Merged]] = List(
        work(path = "grandmother/parent"),
        work(path = "grandfather/parent"),
        work(path = "parent/child")
      )
      withLocalMergedWorksIndex { index =>
        insertIntoElasticsearch(index, works: _*)

        val future = service(index).getParentPath("parent/child")

        whenReady(future.failed) {
          _ shouldBe a[RuntimeException]
        }
      }
    }
  }

  describe("The PathService exactPath getter") {
    it("only fetches the work with that exact path, not its children") {
      val expectedWork = work(path = "parent/child")
      val works: List[Work[Merged]] = List(
        work(path = "parent"),
        expectedWork,
        work(path = "parent/child/grandchild")
      )
      withLocalMergedWorksIndex { index =>
        insertIntoElasticsearch(index, works: _*)
        whenReady(service(index).getWorkWithPath("parent/child")) {
          _ shouldBe expectedWork
        }
      }
    }

    it("throws an exception if there are multiple matching exact paths") {
      val works: List[Work[Merged]] = List(
        work(path = "parent/child"),
        work(path = "parent/child")
      )
      withLocalMergedWorksIndex { index =>
        insertIntoElasticsearch(index, works: _*)

        service(index).getWorkWithPath("parent/child"
        ).failed.futureValue shouldBe a[RuntimeException]
      }
    }

    it("throws an exception if no work can be found") {
      val works: List[Work[Merged]] = List(
        work(path = "hello/world")
      )
      withLocalMergedWorksIndex { index =>
        insertIntoElasticsearch(index, works: _*)

        service(index).getWorkWithPath("parent/child"
        ).failed.futureValue shouldBe a[RuntimeException]
      }
    }
  }

  describe("The PathService childPath getter") {
    it("Fetches works whose paths start with the end of this path") {
      val works: List[Work[Merged]] = List(
        work(path = "grandparent"),
        work(path = "grandparent/parent"),
        work(path = "parent/child/grandchild1"),
        work(path = "parent/child/grandchild2"),
        work(path = "grandparent/parent/child/grandchild3"), // ignored - it has already been resolved up to grandparent
        work(path = "child/grandchild3") // ignored - could be child of a different parent
      )
      withLocalMergedWorksIndex { index =>
        insertIntoElasticsearch(index, works: _*)
        whenReady(service(index).getChildWorks("grandparent/parent")) {
          _ should contain theSameElementsAs List(works(2), works(3))
        }
      }
    }
    it("Returns an empty list if there are no children") {
      val works: List[Work[Merged]] = List(
        work(path = "grandparent"),
        work(path = "grandparent/parent")
      )
      withLocalMergedWorksIndex { index =>
        insertIntoElasticsearch(index, works: _*)
        whenReady(service(index).getChildWorks("grandparent/parent")) {
          _ shouldBe empty
        }
      }
    }
  }
}
