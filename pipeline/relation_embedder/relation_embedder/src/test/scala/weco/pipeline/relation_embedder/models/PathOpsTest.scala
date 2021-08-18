package weco.pipeline.relation_embedder.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class PathOpsTest extends AnyFunSpec with Matchers {
  import PathOps._

  it("finds the parent of a path") {
    "PP/CRI/J/2/3".parent shouldBe "PP/CRI/J/2"
  }

  it("creates a parent mapping") {
    val paths = Set("A/B", "A/B/1", "A/B/2", "A/B/2/1", "A/B/2/2", "A/B/3/1")

    paths.parentMapping shouldBe Map("A/B/1" -> "A/B", "A/B/2" -> "A/B", "A/B/2/1" -> "A/B/2", "A/B/2/2" -> "A/B/2")
  }
}
