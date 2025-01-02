package weco.pipeline.batcher

import org.scalatest.Assertion
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class SelectorTest extends AnyFunSpec with Matchers {

  import Selector._

  /** The following tests use paths representing this tree:
    * {{{
    * A
    * |
    * |-------------
    * |  |         |
    * B  C         E
    * |  |------   |---------
    * |  |  |  |   |  |  |  |
    * D  X  Y  Z   1  2  3  4
    * }}}
    */
  describe("generating selectors for individual paths") {
    it(
      "generates selectors for the parent, siblings, and descendants of a single path"
    ) {
      val path = PathFromString("A/C")
      Selector.forPaths(List(path)) should contain theSameElementsAs List(
        Node("A") -> path,
        Children("A") -> path,
        Descendents("A/C") -> path
      )
    }

    it("generates the whole tree selector for the root path") {
      val rootPath = PathFromString("A")
      Selector.forPaths(List(rootPath)) should contain theSameElementsAs List(
        Tree("A") -> rootPath
      )
    }
  }

  describe("generating selectors for multiple paths") {
    def assertSelectorsForPaths(
      pathStrings: List[String],
      expected: List[(Selector, Int)]
    ): Assertion = {
      val stringsAsPaths = pathStrings.map(PathFromString)
      val expectedPaths = expected.map {
        case (selector, index) => (selector, stringsAsPaths(index))
      }
      Selector.forPaths(
        stringsAsPaths
      ) should contain theSameElementsAs expectedPaths
    }

    it("generates selectors where there are no duplicates") {
      assertSelectorsForPaths(
        List("A/C", "A/E"),
        List(
          Node("A") -> 0,
          Children("A") -> 0,
          Descendents("A/C") -> 0,
          Descendents("A/E") -> 1
        )
      )
    }

    it(
      "generates selectors for multiple paths filtering any unnecessary selectors"
    ) {
      assertSelectorsForPaths(
        List("A/C", "A/C/X", "A/C/Y", "A/E", "A/E/3"),
        List(
          Node("A") -> 0,
          Children("A") -> 0,
          Descendents("A/C") -> 0,
          Descendents("A/E") -> 3
        )
      )
    }

    it("generates selector for whole tree when root is in the path") {
      assertSelectorsForPaths(
        List("A/E/1", "A/B", "A", "A/B/D"),
        List(
          Tree("A") -> 2
        )
      )
    }

    it("generates selector for multiple disjoint trees") {
      assertSelectorsForPaths(
        List("A/E", "A/E/2", "Other/Tree"),
        List(
          Node("A") -> 0,
          Children("A") -> 0,
          Descendents("A/E") -> 0,
          Node("Other") -> 2,
          Children("Other") -> 2,
          Descendents("Other/Tree") -> 2
        )
      )
    }
  }
}
