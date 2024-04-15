package weco.pipeline.transformer.marc_common.transformers

import org.scalatest.LoneElement
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.internal_model.work.Person
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcSubfield}

class MarcPersonTest
    extends MarcAbstractAgentBehaviours
    with LoneElement
    with TableDrivenPropertyChecks {
  describe("extracting Persons from MARC fields 100, 600 and 700") {
    info("https://www.loc.gov/marc/bibliographic/bdx00.html")
    info("https://www.loc.gov/marc/bibliographic/bd100.html")
    info("https://www.loc.gov/marc/bibliographic/bd600.html")
    info("https://www.loc.gov/marc/bibliographic/bd700.html")

    it should behave like abstractAgentTransformer(
      MarcPerson,
      "Person",
      "100"
    )
    describe("permitted marcTags") {
      forAll(
        Table("marcTag", "100", "600", "700")
      ) {
        marcTag =>
          it(s"creates a Person given a field with tag $marcTag") {
            MarcPerson(
              MarcField(
                marcTag = marcTag,
                subfields = Seq(
                  MarcSubfield(
                    tag = "a",
                    content = "Ekalavya"
                  )
                )
              )
            ).get shouldBe a[Person[_]]
          }
      }
    }
    val labelSubfieldTags = Seq(
      "a",
      "b",
      "c",
      "d",
      "t",
      "n",
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

      val nonLabelSubfields = Seq("m", "0", "u") map {
        tag =>
          MarcSubfield(
            tag = tag,
            content = tag.toUpperCase()
          )
      }

      MarcPerson(
        MarcField(
          marcTag = "700",
          subfields = labelSubfields ++ nonLabelSubfields
        )
      ).get.label shouldBe expectedLabel
    }

  }

}
