package weco.pipeline.transformer.marc.xml.transformers

import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.DataState
import weco.catalogue.internal_model.work.WorkData
import weco.pipeline.transformer.marc.xml.data.MarcXMLRecord

class MarcXMLRecordTransformerTest extends AnyFunSpec with Matchers {

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
          <datafield tag ="250">
            <subfield code="a">Director's cut</subfield>
          </datafield>
        </record>
      )
    )

    it("extracts ISBN and ISSN") {
      work.data.otherIdentifiers.map(
        _.value
      ) should contain theSameElementsAs Seq("8601416781396", "1477-4615")
    }

    it("extracts the edition statement") {
      work.data.edition.get shouldBe "Director's cut"
    }
  }
}
