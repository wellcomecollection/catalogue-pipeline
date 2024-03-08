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
      // TODO: The SUT currently assigns a type of EBSCO to the source identifier, but it shouldn't
      //    that belongs in the ebsco-specific transformer.  Sort that all out when it's time.

      work.data should equal(
        WorkData[DataState.Unidentified](title = Some("matacologian"))
      )
    }
  }
}
