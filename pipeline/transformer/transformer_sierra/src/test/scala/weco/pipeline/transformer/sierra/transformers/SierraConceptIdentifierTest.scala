package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.{
  IdentifierType,
  SourceIdentifier
}
import weco.sierra.models.marc.VarField

class SierraConceptIdentifierTest extends AnyFunSpec with Matchers {
  val ontologyType = "Concept"

  it("finds an LCSH identifier") {
    val varField = create655VarFieldWith(indicator2 = "0")

    val expectedSourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType.LCSubjects,
      value = "lcsh/123",
      ontologyType = ontologyType
    )

    val actualSourceIdentifier = SierraConceptIdentifier
      .maybeFindIdentifier(
        varField = varField,
        identifierSubfieldContent = "lcsh/123",
        ontologyType = ontologyType
      )
      .get

    actualSourceIdentifier shouldBe expectedSourceIdentifier
  }

  it("finds a MESH identifier") {
    val varField = create655VarFieldWith(indicator2 = "2")

    val expectedSourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType.MESH,
      value = "mesh/456",
      ontologyType = ontologyType
    )

    val actualSourceIdentifier = SierraConceptIdentifier
      .maybeFindIdentifier(
        varField = varField,
        identifierSubfieldContent = "mesh/456",
        ontologyType = ontologyType
      )
      .get

    actualSourceIdentifier shouldBe expectedSourceIdentifier
  }

  it("finds a no-ID identifier if indicator 2 = 4") {
    val varField = create655VarFieldWith(indicator2 = "4")

    SierraConceptIdentifier.maybeFindIdentifier(
      varField = varField,
      identifierSubfieldContent = "noid/000",
      ontologyType = ontologyType
    ) shouldBe None
  }

  it("returns None if indicator 2 is empty") {
    val varField = create655VarFieldWith(indicator2 = None)

    SierraConceptIdentifier.maybeFindIdentifier(
      varField = varField,
      identifierSubfieldContent = "lcsh/789",
      ontologyType = ontologyType
    ) shouldBe None
  }

  it("returns None if it sees an unrecognised identifier scheme") {
    val varField = create655VarFieldWith(indicator2 = "8")

    SierraConceptIdentifier.maybeFindIdentifier(
      varField = varField,
      identifierSubfieldContent = "u/xxx",
      ontologyType = ontologyType
    ) shouldBe None
  }

  it("passes through the ontology type") {
    val varField = create655VarFieldWith(indicator2 = "2")

    val expectedSourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType.MESH,
      value = "mesh/456",
      ontologyType = "Item"
    )

    val actualSourceIdentifier = SierraConceptIdentifier
      .maybeFindIdentifier(
        varField = varField,
        identifierSubfieldContent = "mesh/456",
        ontologyType = "Item"
      )
      .get

    actualSourceIdentifier shouldBe expectedSourceIdentifier
  }

  private def create655VarFieldWith(indicator2: Option[String]): VarField =
    VarField(marcTag = Some("655"), indicator2 = indicator2)

  private def create655VarFieldWith(indicator2: String): VarField =
    create655VarFieldWith(indicator2 = Some(indicator2))
}
