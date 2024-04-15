package weco.pipeline.transformer.marc_common.transformers

import org.scalatest.LoneElement
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.catalogue.internal_model.work.Meeting
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcSubfield}

class MarcMeetingTest
    extends MarcAbstractAgentBehaviours
    with LoneElement
    with TableDrivenPropertyChecks {
  describe("extracting Meetings from MARC fields 111, 611 and 711") {
    info("https://www.loc.gov/marc/bibliographic/bdx11.html")
    info("https://www.loc.gov/marc/bibliographic/bd111.html")
    info("https://www.loc.gov/marc/bibliographic/bd611.html")
    info("https://www.loc.gov/marc/bibliographic/bd711.html")

    it should behave like abstractAgentTransformer(
      MarcMeeting,
      "Meeting",
      "111"
    )

    describe("permitted marcTags") {
      forAll(
        Table("marcTag", "111", "611", "711")
      ) {
        marcTag =>
          it(s"creates a Meeting given a field with tag $marcTag") {
            MarcMeeting(
              MarcField(
                marcTag = marcTag,
                subfields = Seq(
                  MarcSubfield(
                    tag = "a",
                    content = "Entmoot"
                  ),
                  MarcSubfield(
                    tag = "c",
                    content = "Derndingle"
                  ),
                  MarcSubfield(
                    tag = "d",
                    content = "TA 3019"
                  )
                )
              )
            ).get shouldBe an[Meeting[_]]
          }
      }
    }
    
    val labelSubfieldTags = Seq(
      "a",
      "c",
      "d",
      "t"
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

      val nonLabelSubfields = Seq("n", "p", "q") map {
        tag =>
          MarcSubfield(
            tag = tag,
            content = tag.toUpperCase()
          )
      }

      MarcMeeting(
        MarcField(
          marcTag = "711",
          subfields = labelSubfields ++ nonLabelSubfields
        )
      ).get.label shouldBe expectedLabel
    }

  }

}
