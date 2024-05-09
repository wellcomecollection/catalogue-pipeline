package weco.pipeline.transformer.marc_common.transformers

import org.scalatest.{LoneElement, TryValues}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.IdentifierType
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcSubfield}

trait MarcAbstractAgentBehaviours
    extends AnyFunSpec
    with Matchers
    with TryValues
    with LoneElement {
  def abstractAgentTransformer(
    transformer: MarcAbstractAgent,
    ontologyType: String,
    marcTag: String
  ): Unit = {

    describe("error conditions") {
      it("returns an error if called with an inappropriate field") {
        transformer(
          MarcField(
            marcTag = "999",
            subfields = Seq(MarcSubfield(tag = "a", content = "Anne A. Gent"))
          )
        ).failure.exception.getMessage should startWith(
          "attempt to transform incompatible MARC field"
        )
      }

      it("returns an error if no label subfields are present") {
        transformer(
          MarcField(
            marcTag = marcTag,
            subfields = Nil
          )
        ).failure.exception.getMessage should startWith(
          "no label found when transforming"
        )
      }

      it("returns an error if label subfields are empty") {
        transformer(
          MarcField(
            marcTag = marcTag,
            subfields = Seq(MarcSubfield(tag = "a", content = ""))
          )
        ).failure.exception.getMessage should startWith(
          "no label found when transforming"
        )
      }
    }

    describe("extracting a simple label") {
      it("extracts the ǂa subfield as the label") {
        transformer(
          MarcField(
            marcTag = marcTag,
            subfields = Seq(MarcSubfield(tag = "a", content = "Anne A. Gent"))
          )
        ).get should have(
          'label("Anne A. Gent")
        )
      }
    }

    describe("assigning an identifier") {
      it("extracts ǂ0 as the id") {
        transformer(
          MarcField(
            marcTag = marcTag,
            subfields = Seq(
              MarcSubfield(tag = "a", content = "Anne A. Gent"),
              MarcSubfield(tag = "0", content = "no97058352")
            )
          )
        ).get.id.allSourceIdentifiers.loneElement should have(
          'value("no97058352"),
          'ontologyType(ontologyType),
          // Agents with identifiers are assumed to be
          // identified under LCNames
          // This is not strictly true -
          // - 1xx and 7xx have no official defined value for this
          // - in 6xx this is governed by ind2=0
          'identifierType(IdentifierType.LCNames)
        )
      }
      it("generates a label-derived id if there is no ǂ0") {
        transformer(
          MarcField(
            marcTag = marcTag,
            subfields = Seq(
              MarcSubfield(tag = "a", content = "Anne On a Moose")
            )
          )
        ).get.id.allSourceIdentifiers.loneElement should have(
          'value("anne on a moose"),
          'ontologyType(ontologyType),
          // Agents with identifiers are assumed to be identified under LCNames
          'identifierType(IdentifierType.LabelDerived)
        )
      }
    }
  }

}
