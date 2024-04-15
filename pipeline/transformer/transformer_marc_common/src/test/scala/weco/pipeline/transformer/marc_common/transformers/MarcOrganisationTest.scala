package weco.pipeline.transformer.marc_common.transformers

import org.scalatest.LoneElement
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.internal_model.work.Organisation
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcSubfield}

class MarcOrganisationTest
    extends MarcAbstractAgentBehaviours
    with LoneElement
    with TableDrivenPropertyChecks {
  describe("extracting Organisations from MARC fields 110, 610 and 710") {
    info("https://www.loc.gov/marc/bibliographic/bdx10.html")
    info("https://www.loc.gov/marc/bibliographic/bd110.html")
    info("https://www.loc.gov/marc/bibliographic/bd610.html")
    info("https://www.loc.gov/marc/bibliographic/bd710.html")

    it should behave like abstractAgentTransformer(
      MarcOrganisation,
      "Organisation",
      "110"
    )
    describe("permitted marcTags") {

      forAll(
        Table("marcTag", "110", "610", "710")
      ) {
        marcTag =>
          it(s"creates an Organisation given a field with tag $marcTag") {
            MarcOrganisation(
              MarcField(
                marcTag = marcTag,
                subfields = Seq(
                  MarcSubfield(
                    tag = "a",
                    content = "The Illuminati"
                  )
                )
              )
            ).get shouldBe an[Organisation[_]]
          }
      }
    }
    val labelSubfieldTags = Seq(
      "a",
      "b",
      "c",
      "d",
      "t",
      "p",
      "q",
      "l"
    )
    it(
      s"derives the label from subFields ${labelSubfieldTags.mkString(", ")}"
    ) {

      val expectedLabel = labelSubfieldTags.mkString(" ").toUpperCase()
      val labelSubfields = labelSubfieldTags map {
        tag =>
          MarcSubfield(
            tag = tag,
            content = tag.toUpperCase()
          )
      }

      val nonLabelSubfields = Seq("n", "0", "u") map {
        tag =>
          MarcSubfield(
            tag = tag,
            content = tag.toUpperCase()
          )
      }

      MarcOrganisation(
        MarcField(
          marcTag = "710",
          subfields = labelSubfields ++ nonLabelSubfields
        )
      ).get.label shouldBe expectedLabel
    }

  }

}
