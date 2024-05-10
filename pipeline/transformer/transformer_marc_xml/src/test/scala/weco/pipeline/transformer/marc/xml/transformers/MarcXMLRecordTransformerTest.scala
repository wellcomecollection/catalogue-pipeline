package weco.pipeline.transformer.marc.xml.transformers

import org.scalatest.LoneElement
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.locations.DigitalLocation

import weco.pipeline.transformer.marc.xml.data.MarcXMLRecord
import weco.pipeline.transformer.marc_common.logging.LoggingContext

class MarcXMLRecordTransformerTest
    extends AnyFunSpec
    with Matchers
    with LoneElement {

  describe("a maximal XML record") {
    implicit val ctx: LoggingContext = LoggingContext("test")

    val workData = MarcXMLRecordTransformer(
      MarcXMLRecord(
        <record xmlns="http://www.loc.gov/MARC21/slim">
          <controlfield tag="001">3PaDhRp</controlfield>
          <controlfield tag="008">030214c20039999cauar o 0 a0eng c</controlfield>
          <datafield tag ="245">
            <subfield code="a">matacologian</subfield>
          </datafield>
          <datafield tag ="020">
            <subfield code="a">8601416781396</subfield>
          </datafield>
          <datafield tag ="022">
            <subfield code="a">1477-4615</subfield>
          </datafield>
          <datafield tag ="100">
            <subfield code="a">Nicholas Fallaize</subfield>
          </datafield>
          <datafield tag ="110">
            <subfield code="a">SMERSH</subfield>
          </datafield>
          <datafield tag ="111">
            <subfield code="a">Aristotle and Descartes,</subfield>
            <subfield code="c">Glubbdubdrib</subfield>
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
          <datafield tag ="250">
            <subfield code="a">Director's cut</subfield>
          </datafield>
          <datafield tag ="310">
            <subfield code="a">Sizdah Behar on even-numbered years</subfield>
          </datafield>
          <datafield tag ="362">
            <subfield code="a">NX-326</subfield>
          </datafield>
          <datafield tag ="520">
            <subfield code="a">Some of them [sc. physicians] I know are ignorant beyond Description.</subfield>
          </datafield>
          <datafield tag ="600">
            <subfield code="a">William Burroughs</subfield>
          </datafield>
          <datafield tag ="610">
            <subfield code="a">Umbrella Corporation</subfield>
          </datafield>
          <datafield tag ="611">
            <subfield code="a">Derndingle Entmoot 3019</subfield>
          </datafield>
          <datafield tag ="648">
            <subfield code="a">Fourth Millenium of the Third Age</subfield>
          </datafield>
          <datafield tag ="650">
            <subfield code="a">Effective Homeopathy</subfield>
          </datafield>
          <datafield tag ="651">
            <subfield code="a">Houyhnhnm Land</subfield>
          </datafield>
          <datafield tag ="655">
            <subfield code="a">Lo-Fi Darkwave</subfield>
          </datafield>
          <datafield tag ="700">
            <subfield code="a">Nora Helmer</subfield>
          </datafield>
          <datafield tag ="710">
            <subfield code="a">SPECTRE</subfield>
          </datafield>
          <datafield tag ="711">
            <subfield code="a">James Barry and Florence Nightingale,</subfield>
            <subfield code="c">waiting for a train</subfield>
          </datafield>
          <datafield tag ="856">
            <subfield code="y">Hampster Dance</subfield>
            <subfield code="u">https://example.com/hampsterdance</subfield>
          </datafield>
        </record>
      )
    )

    it("extracts alternative titles") {
      workData.alternativeTitles should contain theSameElementsAs Seq(
        "LLyfr Coch",
        "Red Book",
        "Mabinogion"
      )
    }

    it("extracts ISBN and ISSN") {
      workData.otherIdentifiers.map(
        _.value
      ) should contain theSameElementsAs Seq("8601416781396", "1477-4615")
    }

    it("extracts language") {
      workData.languages.map(
        _.id
      ) should contain theSameElementsAs Seq("eng")
    }

    it("extracts the current frequency") {
      workData.currentFrequency.get shouldBe "Sizdah Behar on even-numbered years"
    }

    it("extracts the edition statement") {
      workData.edition.get shouldBe "Director's cut"
    }

    it("extracts the designation") {
      workData.designation.loneElement shouldBe "NX-326"
    }

    it("extracts a description") {
      workData.description.get shouldBe "<p>Some of them [sc. physicians] I know are ignorant beyond Description.</p>"
    }

    it("extracts an electronic resource") {
      val resource = workData.items.loneElement
      resource.title.get shouldBe "Hampster Dance"
      resource.locations.loneElement
        .asInstanceOf[DigitalLocation]
        .url shouldBe "https://example.com/hampsterdance"
    }

    it("extracts contributors") {
      workData.contributors.map(
        _.agent.label
      ) should contain theSameElementsAs Seq(
        "Nicholas Fallaize",
        "SMERSH",
        "Aristotle and Descartes, Glubbdubdrib",
        "Nora Helmer",
        "SPECTRE",
        "James Barry and Florence Nightingale, waiting for a train"
      )
    }

    it("extracts subjects") {
      workData.subjects.map(
        _.label
      ) should contain theSameElementsAs Seq(
        "William Burroughs",
        "Umbrella Corporation",
        "Derndingle Entmoot 3019",
        "Fourth Millenium of the Third Age",
        "Effective Homeopathy",
        "Houyhnhnm Land"
      )
    }
    it("extracts genres") {
      workData.genres.loneElement.label shouldBe "Lo-Fi Darkwave"
    }
  }
}
