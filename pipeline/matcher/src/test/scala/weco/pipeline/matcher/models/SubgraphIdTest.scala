package weco.pipeline.matcher.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.pipeline.matcher.generators.WorkStubGenerators

class SubgraphIdTest extends AnyFunSpec with Matchers with WorkStubGenerators {
  it("creates a different component ID when the IDs change") {
    SubgraphId(idA) should not be SubgraphId(idA, idB)
  }

  it("gives the same component ID regardless of ordering") {
    val orderings = Set(
      SubgraphId(idA, idB, idC),
      SubgraphId(idA, idC, idB),
      SubgraphId(idB, idA, idC),
      SubgraphId(idB, idC, idA),
      SubgraphId(idC, idA, idB),
      SubgraphId(idC, idB, idA),
    )

    orderings should have size 1
  }
}
