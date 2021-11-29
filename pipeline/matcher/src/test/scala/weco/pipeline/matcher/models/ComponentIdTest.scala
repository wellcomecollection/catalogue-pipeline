package weco.pipeline.matcher.models

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.pipeline.matcher.generators.WorkStubGenerators

class ComponentIdTest extends AnyFunSpec with Matchers with WorkStubGenerators {
  it("creates a different component ID when the IDs change") {
    ComponentId(idA) should not be ComponentId(idA, idB)
  }

  it("gives the same component ID regardless of ordering") {
    val orderings = Set(
      ComponentId(idA, idB, idC),
      ComponentId(idA, idC, idB),
      ComponentId(idB, idA, idC),
      ComponentId(idB, idC, idA),
      ComponentId(idC, idA, idB),
      ComponentId(idC, idB, idA),
    )

    orderings should have size 1
  }
}
