package weco.pipeline.transformer.calm.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{IdState, IdentifierType, SourceIdentifier}
import weco.catalogue.internal_model.work.{Agent, Contributor}
import weco.catalogue.source_model.generators.CalmRecordGenerators

class CalmContributorsTest
    extends AnyFunSpec
    with Matchers
    with CalmRecordGenerators {
  it("returns an empty list if there's nothing in 'CreatorName'") {
    val record = createCalmRecord

    CalmContributors(record) shouldBe empty
  }

  it("creates an Agent for every entry in 'CreatorName'") {
    val record = createCalmRecordWith(
      "CreatorName" -> "Gabrielle Enthoven",
      "CreatorName" -> "Simone Berbain"
    )

    CalmContributors(record) shouldBe List(
      Contributor(
        id = IdState.Unidentifiable,
        agent = Agent(
          id = IdState.Identifiable(
            sourceIdentifier = SourceIdentifier(
              identifierType = IdentifierType.LabelDerived,
              value = "gabrielle enthoven",
              ontologyType = "Agent"
            )
          ),
          label = "Gabrielle Enthoven"
        )
      ),
      Contributor(
        id = IdState.Unidentifiable,
        agent = Agent(
          id = IdState.Identifiable(
            sourceIdentifier = SourceIdentifier(
              identifierType = IdentifierType.LabelDerived,
              value = "simone berbain",
              ontologyType = "Agent"
            )
          ),
          label = "Simone Berbain"
        )
      ),
    )
  }
}
