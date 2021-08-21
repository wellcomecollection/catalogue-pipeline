package weco.pipeline.relation_embedder.models

import org.apache.commons.io.IOUtils
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.nio.charset.StandardCharsets
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global

class PathCollectionTest extends AnyFunSpec with Matchers {
  it("creates a parent mapping") {
    val paths = PathCollection(
      Set("A/B", "A/B/1", "A/B/2", "A/B/2/1", "A/B/2/2", "A/B/3/1")
    )

    paths.parentMapping shouldBe Map(
      "A/B/1" -> "A/B",
      "A/B/2" -> "A/B",
      "A/B/2/1" -> "A/B/2",
      "A/B/2/2" -> "A/B/2")
  }

  it("creates a child mapping") {
    val paths = PathCollection(
      Set("A/B", "A/B/1", "A/B/2", "A/B/2/1", "A/B/2/2", "A/B/3/1")
    )

    paths.childMapping shouldBe Map(
      "A/B" -> List("A/B/1", "A/B/2"),
      "A/B/1" -> List(),
      "A/B/2" -> List("A/B/2/1", "A/B/2/2"),
      "A/B/2/1" -> List(),
      "A/B/2/2" -> List(),
      "A/B/3/1" -> List()
    )
  }

  // This is a real set of nearly 7000 paths from SAFPA.  This test is less focused on
  // the exact result, more that it returns in a reasonable time.
  //
  // Some refactoring of the relation embedder code accidentally made the childMapping
  // explode in runtime, effectively breaking the relation embedder for large collections.
  //
  // The exact timeout on the Future isn't important and can be adjusted slightly if
  // it's a bit slow on CI, as long as it's not ridiculous.
  it("creates a child mapping in a fast time") {
    val paths = IOUtils
      .resourceToString("/paths.txt", StandardCharsets.UTF_8)
      .split("\n")
      .toSet

    val future = Future {
      PathCollection(paths).childMapping
    }

    Await.result(future, atMost = 5.seconds)
  }

  it("finds the siblings of a path") {
    val paths = PathCollection(
      Set(
        "A/B",
        "A/B/1",
        "A/B/2",
        "A/B/2/2",
        "A/B/3",
        "A/B/3/1",
        "A/B/4",
        "A/B/4/1"))

    paths.siblingsOf("A/B/1") shouldBe (
      (
        List(),
        List("A/B/2", "A/B/3", "A/B/4")))
    paths.siblingsOf("A/B/3") shouldBe ((List("A/B/1", "A/B/2"), List("A/B/4")))
    paths.siblingsOf("A/B/4") shouldBe (
      (
        List("A/B/1", "A/B/2", "A/B/3"),
        List()))

    paths.siblingsOf("A/B/2/2") shouldBe ((List(), List()))

    paths.siblingsOf("doesnotexist") shouldBe ((List(), List()))
  }

  it("finds the children of a path") {
    val paths = PathCollection(
      Set(
        "A/B",
        "A/B/1",
        "A/B/2",
        "A/B/2/2",
        "A/B/3",
        "A/B/3/1",
        "A/B/4",
        "A/B/4/1"))

    paths.childrenOf("A/B") shouldBe List("A/B/1", "A/B/2", "A/B/3", "A/B/4")
    paths.childrenOf("A/B/1") shouldBe empty
    paths.childrenOf("A/B/2") shouldBe List("A/B/2/2")
    paths.childrenOf("A/B/4/1") shouldBe empty
  }

  it("finds the known descendents of a path") {
    val paths = PathCollection(
      Set(
        "A",
        "A/B",
        "A/B/1",
        "A/B/1/2/3",
        "A/B/1/2/2",
        "A/B/1/2/3/4",
        "A/B/1/2/3/5",
        "A/B/2"))

    paths.knownDescendentsOf("A") shouldBe List("A/B", "A/B/1", "A/B/2")
    paths.knownDescendentsOf("A/B") shouldBe List("A/B/1", "A/B/2")
    paths.knownDescendentsOf("A/B/1") shouldBe empty

    paths.knownDescendentsOf("A/B/1/2/3") shouldBe List(
      "A/B/1/2/3/4",
      "A/B/1/2/3/5")
  }

  it("finds the known ancestors of a path") {
    val paths = PathCollection(
      Set("A", "A/B", "A/B/1/2", "A/B/1/2/3", "A/B/1/2/2", "A/B/1/2/3/4")
    )

    paths.knownAncestorsOf("A") shouldBe List()
    paths.knownAncestorsOf("A/B") shouldBe List("A")
    paths.knownAncestorsOf("A/B/1/2") shouldBe List()
    paths.knownAncestorsOf("A/B/1/2/3") shouldBe List("A/B/1/2")
    paths.knownAncestorsOf("A/B/1/2/2") shouldBe List("A/B/1/2")
    paths.knownAncestorsOf("A/B/1/2/3/4") shouldBe List("A/B/1/2", "A/B/1/2/3")
  }
}
