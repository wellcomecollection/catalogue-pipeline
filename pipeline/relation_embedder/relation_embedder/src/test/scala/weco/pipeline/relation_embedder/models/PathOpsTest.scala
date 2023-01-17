package weco.pipeline.relation_embedder.models

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class PathOpsTest extends AnyFunSpec with Matchers with ScalaFutures {
  import PathOps._

  it("finds the parent of a path") {
    "PP/CRI/J/2/3".parent shouldBe "PP/CRI/J/2"
  }

  it("finds the depth of a path") {
    "PP/CRI/J/2/3".depth shouldBe 4
  }
}
