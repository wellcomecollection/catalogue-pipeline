package weco.pipeline.path_concatenator

import org.scalatest.matchers.should.Matchers
import org.scalatest.funspec.AnyFunSpec
import weco.catalogue.internal_model.work.CollectionPath
import weco.catalogue.internal_model.work.generators.WorkGenerators

class ChildWorkTest
  extends AnyFunSpec
  with WorkGenerators
  with Matchers
{
  describe("Concatenating collectionPaths"){

    it("replaces the head of the child's path with the whole of the parent's path") {
      val newChild = ChildWork(
        mergedWork().collectionPath(CollectionPath("a/b")),
        mergedWork().collectionPath(CollectionPath("b/c"))
      )
      newChild.data.collectionPath.get.path shouldBe "a/b/c"
    }

    it("works with arbitrarily long paths in the child") {
      val newChild = ChildWork(
        mergedWork().collectionPath(CollectionPath("a/b")),
        mergedWork().collectionPath(CollectionPath("b/c/d/e/f"))
      )
      newChild.data.collectionPath.get.path shouldBe "a/b/c/d/e/f"
    }

    it("works with arbitrarily long paths in the parent") {
      val newChild = ChildWork(
        mergedWork().collectionPath(CollectionPath("a/b/c/d/e/f")),
        mergedWork().collectionPath(CollectionPath("f/g"))
      )
      newChild.data.collectionPath.get.path shouldBe "a/b/c/d/e/f/g"
    }

    it("works with multi-character node names") {
      val newChild = ChildWork(
        mergedWork().collectionPath(CollectionPath("hello/world")),
        mergedWork().collectionPath(CollectionPath("world/cup"))
      )
      newChild.data.collectionPath.get.path shouldBe "hello/world/cup"
    }

    it("does nothing if the collectionPath of the parent consists of a single node") {
      // When the parent is the root of the hierarchy,
      // that's fine, but there is nothing to do.
      val originalChild = mergedWork().collectionPath(CollectionPath("a/b"))
      val newChild = ChildWork(mergedWork().collectionPath(CollectionPath("a")), originalChild)
      newChild should be theSameInstanceAs originalChild
    }

    it("throws an exception if the end of the parent does not match the head of the child") {
      assertThrows[IllegalArgumentException] {
        ChildWork(
          mergedWork().collectionPath(CollectionPath("b/z")),
          mergedWork().collectionPath(CollectionPath("b/c"))
        )
      }
    }

    it("throws an exception if the child path consists of a single node") {
      // The path should end in a node that represents this record.
      // At any level each record knows its parents.
      // If a record presented as a child has a path with a single node,
      // then it it a root node and cannot actually be a child.
      assertThrows[IllegalArgumentException] {
        ChildWork(
          mergedWork().collectionPath(CollectionPath("a/b")),
          mergedWork().collectionPath(CollectionPath("b"))
        )
      }
    }

    it("throws an exception if the parent work has no collectionPath") {
      assertThrows[IllegalArgumentException] {
        ChildWork(
          mergedWork(),
          mergedWork().collectionPath(CollectionPath("a/b"))
        )
      }
    }

    it("throws an exception if the child work has no collectionPath") {
      // Similar to the root scenario, a record presented as a child
      // must have a collectionPath, otherwise it is not part of a hierarchy
      assertThrows[IllegalArgumentException] {
        ChildWork(
          mergedWork().collectionPath(CollectionPath("a/b")),
          mergedWork()
        )
      }
    }
  }

}
