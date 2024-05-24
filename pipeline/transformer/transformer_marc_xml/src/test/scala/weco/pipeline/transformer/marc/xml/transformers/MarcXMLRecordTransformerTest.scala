package weco.pipeline.transformer.marc.xml.transformers

import org.scalatest.LoneElement
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.catalogue.internal_model.identifiers.IdState.Unidentifiable
import weco.catalogue.internal_model.locations.DigitalLocation
import weco.catalogue.internal_model.work.{
  Agent,
  CollectionPath,
  Concept,
  InstantRange,
  Period,
  Place,
  ProductionEvent,
  Relations,
  SeriesRelation
}
import weco.pipeline.transformer.marc.xml.data.MarcXMLRecord
import weco.pipeline.transformer.marc_common.logging.LoggingContext

import java.time.Instant

class MarcXMLRecordTransformerTest
    extends AnyFunSpec
    with Matchers
    with LoneElement {

  describe("a maximal XML record") {
    implicit val ctx: LoggingContext = LoggingContext("test")

    val modifiedTime = Instant.parse("2021-04-01T12:00:00Z")
    val version = 2131248

    val work = MarcXMLRecordTransformer(
      record = MarcXMLRecord(
        <record xmlns="http://www.loc.gov/MARC21/slim">
          <leader>00000cas a22000003a 4500</leader>
          <controlfield tag="001">3PaDhRp</controlfield>
          <controlfield tag="006">m\\\\\o\\d\\||||||</controlfield>
          <controlfield tag="008">030214c20039999cauar o 0 a0eng c</controlfield>
          <datafield tag="245">
            <subfield code="a">matacologian</subfield>
          </datafield>
          <datafield tag="020">
            <subfield code="a">8601416781396</subfield>
          </datafield>
          <datafield tag="022">
            <subfield code="a">1477-4615</subfield>
          </datafield>
          <datafield tag="100">
            <subfield code="a">Nicholas Fallaize</subfield>
          </datafield>
          <datafield tag="110">
            <subfield code="a">SMERSH</subfield>
          </datafield>
          <datafield tag="111">
            <subfield code="a">Aristotle and Descartes,</subfield>
            <subfield code="c">Glubbdubdrib</subfield>
          </datafield>
          <datafield tag="130">
            <subfield code="a">LLyfr Coch</subfield>
          </datafield>
          <datafield tag="240">
            <subfield code="a">Red Book</subfield>
          </datafield>
          <datafield tag="246">
            <subfield code="a">Mabinogion</subfield>
          </datafield>
          <datafield tag="250">
            <subfield code="a">Director's cut</subfield>
          </datafield>
          <datafield tag="260">
            <subfield code="a">London :</subfield>
            <subfield code="b">Arts Council of Great Britain,</subfield>
            <subfield code="c">1976;</subfield>
            <subfield code="e">Twickenham :</subfield>
            <subfield code="f">CTD Printers,</subfield>
            <subfield code="g">1974</subfield>
          </datafield>
          <datafield tag="260">
            <subfield code="a">Bethesda, Md. :</subfield>
            <subfield code="b">Toxicology Information Program, National Library of Medicine [producer] ;</subfield>
            <subfield code="a">Springfield, Va. :</subfield>
            <subfield code="b">National Technical Information Service [distributor],</subfield>
            <subfield code="c">1974-</subfield>
          </datafield>
          <datafield tag="310">
            <subfield code="a">Sizdah Behar on even-numbered years</subfield>
          </datafield>
          <datafield tag="362">
            <subfield code="a">NX-326</subfield>
          </datafield>
          <datafield tag ="440">
            <subfield code="a">A Series</subfield>
          </datafield>
          <datafield tag="520">
            <subfield code="a">Some of them [sc. physicians] I know are ignorant beyond Description.</subfield>
          </datafield>
          <datafield tag="600" ind2="0">
            <subfield code="a">William Burroughs</subfield>
          </datafield>
          <datafield tag="610" ind2="0">
            <subfield code="a">Umbrella Corporation</subfield>
          </datafield>
          <datafield tag="611" ind2="0">
            <subfield code="a">Derndingle Entmoot 3019</subfield>
          </datafield>
          <datafield tag="648" ind2="0">
            <subfield code="a">Fourth Millenium of the Third Age</subfield>
          </datafield>
          <datafield tag="650"  ind2="0">
            <subfield code="a">Effective Homeopathy</subfield>
          </datafield>
          <datafield tag="651" ind2="0">
            <subfield code="a">Houyhnhnm Land</subfield>
          </datafield>
          <datafield tag="655">
            <subfield code="a">Lo-Fi Darkwave</subfield>
          </datafield>
          <datafield tag="700">
            <subfield code="a">Nora Helmer</subfield>
          </datafield>
          <datafield tag="710">
            <subfield code="a">SPECTRE</subfield>
          </datafield>
          <datafield tag="711">
            <subfield code="a">James Barry and Florence Nightingale,</subfield>
            <subfield code="c">waiting for a train</subfield>
          </datafield>
          <datafield tag="773">
            <subfield code="w">parent_id</subfield>
            <subfield code="g">Vol. 24, pt. B no. 9 (Sept. 1993), p. 235-48</subfield>
          </datafield>
          <datafield tag="774">
            <subfield code="t">View E from rooftop of garden bounded by Bruckner Expressway</subfield>
            <subfield code="w">constituent_unit_1</subfield>
          </datafield>
          <datafield tag="856">
            <subfield code="y">Hampster Dance</subfield>
            <subfield code="u">https://example.com/hampsterdance</subfield>
          </datafield>
        </record>
      ),
      version = version,
      modifiedTime = modifiedTime
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

    it("extracts language") {
      work.data.languages.map(
        _.id
      ) should contain theSameElementsAs Seq("eng")
    }

    it("extracts the current frequency") {
      work.data.currentFrequency.get shouldBe "Sizdah Behar on even-numbered years"
    }

    it("extracts the edition statement") {
      work.data.edition.get shouldBe "Director's cut"
    }

    it("extracts the designation") {
      work.data.designation.loneElement shouldBe "NX-326"
    }

    it("extracts a description") {
      work.data.description.get shouldBe "<p>Some of them [sc. physicians] I know are ignorant beyond Description.</p>"
    }

    it("extracts an electronic resource") {
      val resource = work.data.items.loneElement
      resource.title.get shouldBe "Hampster Dance"
      resource.locations.loneElement
        .asInstanceOf[DigitalLocation]
        .url shouldBe "https://example.com/hampsterdance"
    }

    it("extracts contributors") {
      work.data.contributors.map(
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
      work.data.subjects.map(
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
      work.data.genres.loneElement.label shouldBe "Lo-Fi Darkwave"
    }

    it("extracts format") {
      work.data.format.get.label shouldBe "E-journals"
    }

    it("extracts production") {
      work.data.production shouldBe List(
        ProductionEvent(
          "London : Arts Council of Great Britain, 1976; Twickenham : CTD Printers, 1974",
          List(
            Place(Unidentifiable, "London"),
            Place(Unidentifiable, "Twickenham")
          ),
          List(
            Agent(Unidentifiable, "Arts Council of Great Britain"),
            Agent(Unidentifiable, "CTD Printers")
          ),
          List(
            Period(Unidentifiable, "1976;", None),
            Period(
              Unidentifiable,
              "1974",
              Some(
                InstantRange(
                  from = Instant.parse("1974-01-01T00:00:00Z"),
                  to = Instant.parse("1974-12-31T23:59:59.999999999Z"),
                  label = "1974"
                )
              )
            )
          ),
          Some(Concept(Unidentifiable, "Manufacture"))
        ),
        ProductionEvent(
          "Bethesda, Md. : Toxicology Information Program, National Library of Medicine [producer] ; Springfield, Va. : National Technical Information Service [distributor], 1974-",
          List(
            Place(Unidentifiable, "Bethesda, Md."),
            Place(Unidentifiable, "Springfield, Va.")
          ),
          List(
            Agent(
              Unidentifiable,
              "Toxicology Information Program, National Library of Medicine [producer] ;"
            ),
            Agent(
              Unidentifiable,
              "National Technical Information Service [distributor]"
            )
          ),
          List(
            Period(
              Unidentifiable,
              "1974-",
              Some(
                InstantRange(
                  Instant.parse("1974-01-01T00:00:00Z"),
                  Instant.parse("9999-12-31T23:59:59.999999999Z"),
                  label = "1974-"
                )
              )
            )
          ),
          None
        )
      )
    }

    it("extracts the collection path") {
      work.data.collectionPath.get shouldBe CollectionPath(
        path = "parent_id/Vol_24_pt_B_no_9_Sept_1993_p_23548_3PaDhRp",
        label = None
      )
    }

    it("sets the relations on state") {
      work.state.relations shouldBe Relations(
        ancestors = List(
          SeriesRelation("A Series")
        )
      )
    }
  }
}
