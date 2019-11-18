package uk.ac.wellcome.platform.transformer.mets.parsers

import org.scalatest.{FunSpec, Matchers}

import scala.io.Source
import scala.util.Failure

class MetsXmlParserTest extends FunSpec with Matchers {

  it("parses recordIdentifier from XML") {
    MetsXmlParser(xml).get.recordIdentifier shouldBe "b30246039"
  }

  it("does not parse a mets if recordIdentifier is outside of dmdSec element"){
    MetsXmlParser(xmlNodmdSec) shouldBe a [Failure[_]]
  }

  it("does not parse if there is more than one recordIdentifier"){
    MetsXmlParser(xmlMultipleIds) shouldBe a [Failure[_]]
  }

  it("parses accessCondition from XML") {
    MetsXmlParser(xml).get.accessCondition shouldBe Some("CC-BY-NC")
  }

  it("parses a METS with no access condition") {
    MetsXmlParser(xmlNoLicense).get.accessCondition shouldBe None
  }

  it("does not parse a METS with no multiple licenses") {
    MetsXmlParser(xmlMultipleLicense) shouldBe a [Failure[_]]
  }


  val xml =
    Source
      .fromInputStream(getClass.getResourceAsStream("/b30246039.xml"))
      .getLines
      .mkString

  val xmlNodmdSec =
    s"""
       |<mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:mods="http://www.loc.gov/mods/v3">
       |  <mods:recordIdentifier source="gbv-ppn">b30246039</mods:recordIdentifier>
       |</mets:mets>
       |""".stripMargin

  val xmlMultipleIds =
    s"""
       |<mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:mods="http://www.loc.gov/mods/v3">
       |  <mets:dmdSec ID="DMDLOG_0000">
       |    <mets:mdWrap MDTYPE="MODS">
       |      <mets:xmlData>
       |        <mods:mods>
       |          <mods:recordInfo>
       |            <mods:recordIdentifier source="gbv-ppn">b30246039</mods:recordIdentifier>
       |            <mods:recordIdentifier source="gbv-ppn">b3024346567</mods:recordIdentifier>
       |          </mods:recordInfo>
       |        </mods:mods>
       |      </mets:xmlData>
       |    </mets:mdWrap>
       |  </mets:dmdSec>
       |</mets:mets>
       |""".stripMargin

  val xmlNoLicense =
    s"""
       |<mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:mods="http://www.loc.gov/mods/v3">
       |<mets:dmdSec ID="DMDLOG_0000">
       |  <mets:mdWrap MDTYPE="MODS">
       |    <mets:xmlData>
       |      <mods:mods>
       |        <mods:recordInfo>
       |          <mods:recordIdentifier source="gbv-ppn">b30246039</mods:recordIdentifier>
       |        </mods:recordInfo>
       |      </mods:mods>
       |    </mets:xmlData>
       |  </mets:mdWrap>
       |</mets:dmdSec>
       |</mets:mets>
       |""".stripMargin

  val xmlMultipleLicense =
    s"""
       ||<mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:mods="http://www.loc.gov/mods/v3">
       |  <mets:dmdSec ID="DMDLOG_0000">
       |    <mets:mdWrap MDTYPE="MODS">
       |      <mets:xmlData>
       |        <mods:mods>
       |          <mods:recordInfo>
       |            <mods:recordIdentifier source="gbv-ppn">b30246039</mods:recordIdentifier>
       |          </mods:recordInfo>
       |          <mods:accessCondition type="dz">CC-BY-NC</mods:accessCondition>
       |          <mods:accessCondition type="dz">CC-BY</mods:accessCondition>
       |        </mods:mods>
       |      </mets:xmlData>
       |    </mets:mdWrap>
       |  </mets:dmdSec>
       |</mets:mets>
       |""".stripMargin
}
