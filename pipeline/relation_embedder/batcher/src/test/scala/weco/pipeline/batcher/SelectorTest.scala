package weco.pipeline.batcher

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class SelectorTest extends AnyFunSpec with Matchers {

  import Selector._

  /** The following tests use paths representing this tree:
    *
    * A
    * \|
    * \|-------------
    * \| | | B C E
    * \| |------ |---------
    * \| | | | | | | | D X Y Z 1 2 3 4
    */
  it("generates selectors for a single path") {
    Selector.forPath("A/C") should contain theSameElementsAs List(
      Node("A"),
      Children("A"),
      Descendents("A/C")
    )
  }

  it("generates the whole tree selector for the root path") {
    Selector.forPath("A") should contain theSameElementsAs List(
      Tree("A")
    )
  }

  it("generates selectors for multiple paths without duplicates") {
    Selector.forPaths(List("A/C", "A/E")) should contain theSameElementsAs List(
      Node("A") -> 0,
      Children("A") -> 0,
      Descendents("A/C") -> 0,
      Descendents("A/E") -> 1
    )
  }

  it("generates selectors for multiple paths filtering any unnecessary") {
    Selector.forPaths(
      List("A/C", "A/C/X", "A/C/Y", "A/E", "A/E/3")
    ) should contain theSameElementsAs List(
      Node("A") -> 0,
      Children("A") -> 0,
      Descendents("A/C") -> 0,
      Descendents("A/E") -> 3
    )
  }

  it("generates selector for whole tree when root is in the path") {
    Selector.forPaths(
      List("A/E/1", "A/B", "A", "A/B/D")
    ) should contain theSameElementsAs List(
      Tree("A") -> 2
    )
  }

  it("generates selector for multiple disjoint trees") {
    Selector.forPaths(
      List("A/E", "A/E/2", "Other/Tree")
    ) should contain theSameElementsAs List(
      Node("A") -> 0,
      Children("A") -> 0,
      Descendents("A/E") -> 0,
      Node("Other") -> 2,
      Children("Other") -> 2,
      Descendents("Other/Tree") -> 2
    )
  }
}
