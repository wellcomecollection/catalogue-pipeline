package weco.pipeline.relation_embedder.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class PathOpsTest extends AnyFunSpec with Matchers {
  import PathOps._

  it("finds the parent of a path") {
    "PP/CRI/J/2/3".parent shouldBe "PP/CRI/J/2"
  }

  it("tests for being a descendent") {
    "PP/CRI/J/2/3".isDescendentOf("PP/CRI/J/2/3") shouldBe false

    "PP/CRI/J/2/3".isDescendentOf("PP/CRI/J/2") shouldBe true
    "PP/CRI/J/2/3".isDescendentOf("PP/CRI/J") shouldBe true
    "PP/CRI/J/2/3".isDescendentOf("PP/CRI") shouldBe true

    "PP/CRI/J/2/3".isDescendentOf("PP/CRI/I/1") shouldBe false

    "PP/CRI/J/2/3".isDescendentOf("PP/CRI/J1") shouldBe false
  }

  it("creates a parent mapping") {
    val paths = Set("A/B", "A/B/1", "A/B/2", "A/B/2/1", "A/B/2/2", "A/B/3/1")

    paths.parentMapping shouldBe Map("A/B/1" -> "A/B", "A/B/2" -> "A/B", "A/B/2/1" -> "A/B/2", "A/B/2/2" -> "A/B/2")
  }

  it("creates a child mapping") {
    val paths = Set("A/B", "A/B/1", "A/B/2", "A/B/2/1", "A/B/2/2", "A/B/3/1")

    paths.childMapping shouldBe Map(
      "A/B" -> List("A/B/1", "A/B/2"),
      "A/B/1" -> List(),
      "A/B/2" -> List("A/B/2/1", "A/B/2/2"),
      "A/B/2/1" -> List(),
      "A/B/2/2" -> List(),
      "A/B/3/1" -> List()
    )
  }

  it("finds the siblings of a path") {
    val paths = Set("A/B", "A/B/1", "A/B/2", "A/B/2/2", "A/B/3", "A/B/3/1", "A/B/4", "A/B/4/1")

    paths.siblingsOf("A/B/1") shouldBe ((List(), List("A/B/2", "A/B/3", "A/B/4")))
    paths.siblingsOf("A/B/3") shouldBe ((List("A/B/1", "A/B/2"), List("A/B/4")))
    paths.siblingsOf("A/B/4") shouldBe ((List("A/B/1", "A/B/2", "A/B/3"), List()))

    paths.siblingsOf("A/B/2/2") shouldBe ((List(), List()))

    paths.siblingsOf("doesnotexist") shouldBe ((List(), List()))
  }

  it("finds the children of a path") {
    val paths = Set("A/B", "A/B/1", "A/B/2", "A/B/2/2", "A/B/3", "A/B/3/1", "A/B/4", "A/B/4/1")

    paths.childrenOf("A/B") shouldBe List("A/B/1", "A/B/2", "A/B/3", "A/B/4")
    paths.childrenOf("A/B/1") shouldBe empty
    paths.childrenOf("A/B/2") shouldBe List("A/B/2/2")
    paths.childrenOf("A/B/4/1") shouldBe empty
  }

  it("finds the descendents of a path") {
    val paths = Set("A/B", "A/B/1", "A/B/2", "A/B/2/1", "A/B/2/2", "A/B/3", "A/B/3/1")

    paths.descendentsOf("A/B") shouldBe List("A/B/1", "A/B/2", "A/B/2/1", "A/B/2/2", "A/B/3", "A/B/3/1")
    paths.descendentsOf("A/B/2") shouldBe List("A/B/2/1", "A/B/2/2")
    paths.descendentsOf("A/B/3/1") shouldBe empty

    paths.descendentsOf("doesnotexist") shouldBe empty
  }

  it("finds the known ancestors of a path") {
    val paths = Set("A", "A/B", "A/B/1/2", "A/B/1/2/3", "A/B/1/2/2", "A/B/1/2/3/4")

    paths.knownAncestorsOf("A") shouldBe List()
    paths.knownAncestorsOf("A/B") shouldBe List("A")
    paths.knownAncestorsOf("A/B/1/2") shouldBe List()
    paths.knownAncestorsOf("A/B/1/2/3") shouldBe List("A/B/1/2")
    paths.knownAncestorsOf("A/B/1/2/2") shouldBe List("A/B/1/2")
    paths.knownAncestorsOf("A/B/1/2/3/4") shouldBe List("A/B/1/2", "A/B/1/2/3")
  }
}
