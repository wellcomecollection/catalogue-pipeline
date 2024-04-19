package weco.pipeline.transformer.sierra.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.internal_model.identifiers.{
  IdState,
  IdentifierType,
  SourceIdentifier
}
import weco.pipeline.transformer.marc_common.models.MarcField
import weco.pipeline.transformer.sierra.data.SierraMarcDataConversions
import weco.sierra.generators.MarcGenerators
import weco.sierra.models.marc.Subfield

class SierraConceptsTest
    extends AnyFunSpec
    with Matchers
    with MarcGenerators
    with TableDrivenPropertyChecks
    with SierraMarcDataConversions {
  private val transformer = new SierraConcepts {
    override def getLabel(field: MarcField): Option[String] = None
    // TODO
  }
  private val unsupportedSchemes = List(
    None,
    Some("1"),
    Some("3"),
    Some("4"),
    Some("5"),
    Some("6"),
    Some("7")
  )
  private val supportedSchemes = List(
    ("0", IdentifierType.LCSubjects),
    ("2", IdentifierType.MESH)
  )

  private val allSchemes =
    unsupportedSchemes ++ supportedSchemes.map(scheme => Some(scheme._1))

  it("extracts identifiers from subfield 0") {
    forAll(
      Table(
        ("indicator2", "identifierType"),
        supportedSchemes: _*
      )
    ) {
      (indicator2: String, identifierType: IdentifierType) =>
        val maybeIdentifiedConcept = transformer.getIdState(
          ontologyType = "Concept",
          field = createVarFieldWith(
            marcTag = "CCC",
            indicator2 = indicator2,
            subfields = List(
              Subfield(tag = "a", content = "pilots"),
              Subfield(tag = "0", content = "sh85102165")
            )
          )
        )

        val sourceIdentifier = SourceIdentifier(
          identifierType = identifierType,
          value = "sh85102165",
          ontologyType = "Concept"
        )

        maybeIdentifiedConcept shouldBe IdState.Identifiable(sourceIdentifier)
    }
  }

  it(
    "creates a label-derived identifier for concepts with no identifier, regardless of scheme"
  ) {
    forAll(
      Table(
        "indicator2",
        allSchemes: _*
      )
    ) {
      indicator2 =>
        val maybeIdentifiedConcept = transformer.getIdState(
          ontologyType = "Concept",
          field = createVarFieldWith(
            marcTag = "CCC",
            indicator2 = indicator2,
            subfields = List(
              Subfield(tag = "a", content = "Who Knows")
            )
          )
        )
        val sourceIdentifier = SourceIdentifier(
          identifierType = IdentifierType.LabelDerived,
          value = "who knows",
          ontologyType = "Concept"
        )

        maybeIdentifiedConcept shouldBe IdState.Identifiable(sourceIdentifier)
    }
  }

  it("normalises and deduplicates identifiers in subfield 0") {
    // Given multiple 0 subfields that all resolve to the same value after normalisation
    // Then that normalised value is the identifier value
    val maybeIdentifiedConcept = transformer.getIdState(
      ontologyType = "Concept",
      field = createVarFieldWith(
        marcTag = "CCC",
        indicator2 = "0",
        subfields = List(
          Subfield(tag = "a", content = "martians"),
          Subfield(tag = "0", content = "sh96010159"),
          Subfield(tag = "0", content = "sh96010159"),
          // Including the (DNLM) prefix
          Subfield(tag = "0", content = "(DNLM)sh96010159"),
          // With trailing punctuation
          Subfield(tag = "0", content = "sh96010159."),
          // Including whitespace
          Subfield(tag = "0", content = "sh 96010159"),
          // Including a MESH URL prefix
          Subfield(
            tag = "0",
            content = "https://id.nlm.nih.gov/mesh/sh96010159"
          )
        )
      )
    )

    val sourceIdentifier = SourceIdentifier(
      identifierType = IdentifierType.LCSubjects,
      value = "sh96010159",
      ontologyType = "Concept"
    )

    maybeIdentifiedConcept shouldBe IdState.Identifiable(sourceIdentifier)
  }

  it("ignores multiple instances of subfield 0 in the otherIdentifiers") {
    // Given multiple 0 subfields containing different values
    // Then the Concept is unidentifiable
    val maybeIdentifiedConcept = transformer.getIdState(
      ontologyType = "Concept",
      field = createVarFieldWith(
        marcTag = "CCC",
        indicator2 = "0",
        subfields = List(
          Subfield(tag = "a", content = "hitchhiking"),
          Subfield(tag = "0", content = "lcsh/xxx"),
          Subfield(tag = "0", content = "lcsh/yyy")
        )
      )
    )

    maybeIdentifiedConcept shouldBe IdState.Unidentifiable
  }

  it("ignores identifiers in unknown schemes") {
    // Given a varfield with an indicator2 other than 0 or 2
    // Then the Concept is unidentifiable

    forAll(
      Table(
        "indicator2",
        unsupportedSchemes: _*
      )
    ) {
      indicator2 =>
        val maybeIdentifiedConcept = transformer.getIdState(
          ontologyType = "Concept",
          field = createVarFieldWith(
            marcTag = "CCC",
            indicator2 = indicator2,
            subfields = List(
              Subfield(tag = "a", content = "hitchhiking"),
              Subfield(tag = "0", content = "dunno/xxx")
            )
          )
        )

        maybeIdentifiedConcept shouldBe IdState.Unidentifiable
    }

  }

}
