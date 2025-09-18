package weco.pipeline.transformer.marc_common.transformers

import org.scalatest.LoneElement
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.locations.DigitalLocation
import weco.catalogue.internal_model.work.Item
import weco.pipeline.transformer.marc_common.generators.MarcTestRecord
import weco.pipeline.transformer.marc_common.logging.LoggingContext
import weco.pipeline.transformer.marc_common.models.{MarcField, MarcSubfield}

import scala.collection.Seq

class MarcElectronicResourcesTest
    extends AnyFunSpec
    with Matchers
    with LoneElement {

  private implicit val ctx: LoggingContext = LoggingContext("")

  describe("extracting Electronic resources from MARC 856 fields") {
    it("turns an 856 into an Item with a DigitalLocation") {
      val record = MarcTestRecord(fields =
        Seq(
          MarcField(
            "856",
            subfields = Seq(
              MarcSubfield(tag = "y", content = "My Homepage"),
              MarcSubfield(tag = "u", content = "https://www.example.com/")
            )
          )
        )
      )
      val item: Item[IdState.Unminted] =
        MarcElectronicResources(record).loneElement
      item.title.get shouldBe "My Homepage"
      item.locations.loneElement
        .asInstanceOf[DigitalLocation]
        .url shouldBe "https://www.example.com/"
    }
  }

  it("returns nothing when a record has no 856 fields") {
    val record = MarcTestRecord(fields =
      Seq(
        MarcField(
          "999",
          subfields =
            Seq(MarcSubfield(tag = "u", content = "https://www.example.com/"))
        )
      )
    )
    MarcElectronicResources(record) shouldBe Nil

  }

  it("returns nothing when an 856 field has no ǂu subfield") {
    val record = MarcTestRecord(fields =
      Seq(
        MarcField(
          "856",
          subfields = Seq(MarcSubfield(tag = "y", content = "My Homepage"))
        )
      )
    )
    MarcElectronicResources(record) shouldBe Nil
  }

  describe("to holdings") {

    it("returns nothing when an 856 field has no ǂz subfield") {
      val record = MarcTestRecord(fields =
        Seq(
          MarcField(
            "856",
            subfields =
              Seq(MarcSubfield(tag = "u", content = "https://www.example.com/"))
          )
        )
      )
      MarcElectronicResources.toHoldings(record) shouldBe Nil
    }

    it("returns nothing when an 856 field has no ǂ3 subfield") {
      val record = MarcTestRecord(fields =
        Seq(
          MarcField(
            "856",
            subfields = Seq(
              MarcSubfield(tag = "u", content = "https://www.example.com/"),
              MarcSubfield(tag = "z", content = "click here")
            )
          )
        )
      )
      MarcElectronicResources.toHoldings(record) shouldBe Nil
    }

    it("only returns holdings with all three subfields") {
      val record = MarcTestRecord(fields =
        Seq(
          MarcField(
            "856",
            subfields = Seq(
              MarcSubfield(tag = "u", content = "https://www.example.com/"),
              MarcSubfield(tag = "z", content = "click here"),
              MarcSubfield(tag = "3", content = "enum")
            )
          )
        )
      )
      val holdings = MarcElectronicResources.toHoldings(record).loneElement
      val location = holdings.location.get.asInstanceOf[DigitalLocation]
      location.url shouldBe "https://www.example.com/"
      location.linkText.get shouldBe "click here"
      holdings.enumeration.loneElement shouldBe "enum"
    }

  }

}
