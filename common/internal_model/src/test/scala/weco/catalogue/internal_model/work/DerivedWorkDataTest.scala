package weco.catalogue.internal_model.work

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import uk.ac.wellcome.models.work.generators.WorkGenerators

class DerivedWorkDataTest
    extends AnyFunSpec
    with Matchers
    with WorkGenerators {

  describe("contributorAgents") {
    it("derives contributorAgents from a heterogenous list of contributors") {
      val agents = List(
        Agent("0048146"),
        Organisation("PKK"),
        Person("Salt Bae"),
        Meeting("Brunch, 18th Jan 2021")
      )
      val work =
        denormalisedWork().contributors(
          agents.map(Contributor(_, roles = Nil)))
      val derivedWorkData = DerivedWorkData(work.data)

      derivedWorkData.contributorAgents shouldBe List(
        "Agent:0048146",
        "Organisation:PKK",
        "Person:Salt Bae",
        "Meeting:Brunch, 18th Jan 2021"
      )
    }
  }
}
