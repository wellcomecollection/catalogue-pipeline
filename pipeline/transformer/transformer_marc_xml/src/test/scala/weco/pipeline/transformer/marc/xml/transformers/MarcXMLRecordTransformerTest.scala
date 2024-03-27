package weco.pipeline.transformer.marc.xml.transformers

import org.scalatest.LoneElement
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.DataState
import weco.catalogue.internal_model.locations.DigitalLocation
import weco.catalogue.internal_model.work.WorkData
import weco.pipeline.transformer.marc.xml.data.MarcXMLRecord

class MarcXMLRecordTransformerTest
    extends AnyFunSpec
    with Matchers
    with LoneElement {

  describe("a minimal XML record") {
    it("generates a Work with a sourceIdentifier") {
      info("at minimum, a Work from an XML record needs an id and a title")
      val work = MarcXMLRecordTransformer(
        MarcXMLRecord(
          <record xmlns="http://www.loc.gov/MARC21/slim">
            <controlfield tag="001">3PaDhRp</controlfield>
            <datafield tag ="245">
              <subfield code="a">matacologian</subfield>
            </datafield>
          </record>
        )
      )
      work.state.sourceIdentifier.value shouldBe "3PaDhRp"
      work.data should equal(
        WorkData[DataState.Unidentified](title = Some("matacologian"))
      )
    }
  }
  describe("a maximal XML record") {
    val work = MarcXMLRecordTransformer(
      MarcXMLRecord(
        <record xmlns="http://www.loc.gov/MARC21/slim">
          <controlfield tag="001">3PaDhRp</controlfield>
          <datafield tag ="245">
            <subfield code="a">matacologian</subfield>
          </datafield>
          <datafield tag ="020">
            <subfield code="a">8601416781396</subfield>
          </datafield>
          <datafield tag ="022">
            <subfield code="a">1477-4615</subfield>
          </datafield>
          <datafield tag ="130">
            <subfield code="a">LLyfr Coch</subfield>
          </datafield>
          <datafield tag ="240">
            <subfield code="a">Red Book</subfield>
          </datafield>
          <datafield tag ="246">
            <subfield code="a">Mabinogion</subfield>
          </datafield>
          <datafield tag ="310">
            <subfield code="a">Sizdah Behar on even-numbered years</subfield>
          </datafield>
          <datafield tag ="856">
            <subfield code="y">Hampster Dance</subfield>
            <subfield code="u">https://example.com/hampsterdance</subfield>
          </datafield>
          <datafield tag ="310">
            <subfield code="a">Sizdah Behar on even-numbered years</subfield>
          </datafield>
          <datafield tag ="856">
            <subfield code="y">Hampster Dance</subfield>
            <subfield code="u">https://example.com/hampsterdance</subfield>
          </datafield>
        </record>
      )
    )

    it("extracts alternative titles") {
      work.data.alternativeTitles should contain theSameElementsAs Seq(
        "LLyfr Coch",
        "Red Book",
        "Mabinogion"
      )
    }

    it("extracts ISBN and ISSN") {
      work.data.otherIdentifiers.map(
        _.value
      ) should contain theSameElementsAs Seq("8601416781396", "1477-4615")
    }
    
    it("extracts the current frequency") {
      work.data.currentFrequency.get shouldBe "Sizdah Behar on even-numbered years"
    }

    it("extracts the current frequency") {
      work.data.currentFrequency.get shouldBe "Sizdah Behar on even-numbered years"
    }

    it("extracts the edition statement") {
      work.data.edition.get shouldBe "Director's cut"
    }

    it("extracts an electronic resource") {
      val resource = work.data.items.loneElement
      resource.title.get shouldBe "Hampster Dance"
      resource.locations.loneElement
        .asInstanceOf[DigitalLocation]
        .url shouldBe "https://example.com/hampsterdance"
    }
  }
}
