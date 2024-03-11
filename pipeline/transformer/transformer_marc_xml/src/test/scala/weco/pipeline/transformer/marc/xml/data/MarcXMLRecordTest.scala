package weco.pipeline.transformer.marc.xml.data

import org.scalatest.LoneElement
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

class MarcXMLRecordTest extends AnyFunSpec with Matchers with LoneElement {
  describe("extracting the leader") {
    it("returns the leader as a string") {
      MarcXMLRecord(
        <record xmlns="http://www.loc.gov/MARC21/slim">
          <leader>00000nas a22000003 4500</leader>
        </record>
      ).leader shouldBe "00000nas a22000003 4500"

    }
  }

  describe("extracting controlfields from an MarcXML Record") {
    it("returns the string value of a control field by its MARC tag") {
      MarcXMLRecord(
        <record xmlns="http://www.loc.gov/MARC21/slim">
          <controlfield tag="001">ebs1234567890e</controlfield>
          <controlfield tag="003">EBZ</controlfield>
        </record>
      ).controlField("003").get shouldBe "EBZ"
    }

    it("returns None if the requested field does not exist") {
      MarcXMLRecord(
        <record>
          <controlfield tag="001">ebs1234567890e</controlfield>
        </record>
      ).controlField("003") shouldBe None
    }

    it("returns None if multiple controlfields with the same tag exist") {
      info("controlfields are expected to be unique")
      MarcXMLRecord(
        <record>
          <controlfield tag="001">hello</controlfield>
          <controlfield tag="001">world</controlfield>
        </record>
      ).controlField("001") shouldBe None
    }
  }

  describe("extracting datafields from an Ebsco MARC record") {
    describe("given a single marcTag") {
      it("returns nothing if no field with the requested tag exists") {
        MarcXMLRecord(
          <record>
            <datafield tag="321">
              <subfield tag="a">1234-5678</subfield>
            </datafield>
          </record>
        ).fieldsWithTag("123") shouldBe empty
      }

      it("returns a single datafield with the requested tag") {
        val datafield = MarcXMLRecord(
          <record  xmlns="http://www.loc.gov/MARC21/slim">
            <datafield tag="022">
              <subfield code="a">1234-5678</subfield>
            </datafield>
            <datafield tag="999">
              <subfield code="x">baad-f00d</subfield>
            </datafield>
          </record>
        ).fieldsWithTag("022").loneElement

        datafield should have(
          'marcTag("022")
        )
        datafield.subfields.loneElement should have(
          'tag("a"),
          'content("1234-5678")
        )
      }

      it("returns multiple datafields with the requested tag") {
        val Seq(first, second) = MarcXMLRecord(
          <record>
            <datafield tag="022">
              <subfield code="a">1234-5678</subfield>
            </datafield>
            <datafield tag="999">
              <subfield code="x">baad-f00d</subfield>
            </datafield>
            <datafield tag="022">
              <subfield code="b">g00d-cafe</subfield>
            </datafield>
          </record>
        ).fieldsWithTag("022")

        first.subfields.loneElement should have(
          'tag("a"),
          'content("1234-5678")
        )
        second.subfields.loneElement should have(
          'tag("b"),
          'content("g00d-cafe")
        )
      }
    }
    describe("given multiple marcTags") {
      it(
        "returns the datafields in document order, ignoring the order of tags in the request"
      ) {
        MarcXMLRecord(
          <record>
            <datafield tag="655" />
            <datafield tag="999" />
            <datafield tag="651" />
            <datafield tag="650" />
          </record>
        ).fieldsWithTags("651", "655", "650").map(_.marcTag) shouldBe Seq(
          "655",
          "651",
          "650"
        )
      }
      it(
        "returns any that it can find, even if not all tags are present"
      ) {
        MarcXMLRecord(
          <record>
            <datafield tag="655" />
            <datafield tag="650" />
            <datafield tag="999" />
            <datafield tag="655" />
            <datafield tag="650" />
          </record>
        ).fieldsWithTags("I'm not there", "655", "650")
          .map(_.marcTag) shouldBe Seq(
          "655",
          "650",
          "655",
          "650"
        )
      }
    }
  }

  describe("extracting subfields from an Ebsco MARC record") {
    it("returns nothing if there are no matching subfields") {
      MarcXMLRecord(
        <record>
          <datafield tag="655">
            <subfield tag="x">Hello Mike</subfield>
          </datafield>
          <datafield tag="651">
            <subfield tag="a">Hello Joe</subfield>
          </datafield>
        </record>
      ).subfieldsWithTags("655" -> "a", "651" -> "x") shouldBe empty
    }
    it("returns the subfields in the requested order") {
      info("this is the same as in Sierra")
      info(
        "it would be nice were this to be in doc order, but it doesn't seem worth the bother"
      )
      MarcXMLRecord(
        <record>
          <datafield tag="650">
            <subfield tag="a">Hello Mike</subfield>
          </datafield>
          <datafield tag="655">
            <subfield tag="a">Hello Robert</subfield>
            <subfield tag="0">n93000068</subfield>
          </datafield>
          <datafield tag="651">
            <subfield tag="a">Hello Joe</subfield>
            <subfield tag="0">n93000065</subfield>
          </datafield>
        </record>
      ).subfieldsWithTags(
        "655" -> "0",
        "651" -> "a",
        "655" -> "a",
        "650" -> "a",
        "650" -> "0"
      ).map(_.content) shouldBe Seq(
        "n93000068",
        "Hello Joe",
        "Hello Robert",
        "Hello Mike"
      )
    }
  }
}
