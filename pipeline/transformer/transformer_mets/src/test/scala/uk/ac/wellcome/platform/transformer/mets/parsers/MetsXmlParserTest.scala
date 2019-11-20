package uk.ac.wellcome.platform.transformer.mets.parsers

import org.apache.commons.io.IOUtils
import org.scalatest.{FunSpec, Matchers}

class MetsXmlParserTest extends FunSpec with Matchers {

  it("parses recordIdentifier from XML") {
    MetsXmlParser(xml).right.get.recordIdentifier shouldBe "b30246039"
  }

  it("does not parse a mets if recordIdentifier is outside of dmdSec element") {
    MetsXmlParser(xmlNodmdSec) shouldBe a[Left[_, _]]
  }

  it("does not parse if there is more than one recordIdentifier") {
    MetsXmlParser(xmlMultipleIds) shouldBe a[Left[_, _]]
  }

  it("parses accessCondition from XML") {
    MetsXmlParser(xml).right.get.accessCondition shouldBe Some("CC-BY-NC")
  }

  it("parses a METS with no access condition") {
    MetsXmlParser(xmlNoLicense).right.get.accessCondition shouldBe None
  }

  it("does not parse a METS with no multiple licenses") {
    MetsXmlParser(xmlMultipleLicense) shouldBe a[Left[_, _]]
  }

  it("fails if the input stream is not an xml") {
    MetsXmlParser("hagdf".toInputStream) shouldBe a[Left[_, _]]
  }

  def xml = getClass.getResourceAsStream("/b30246039.xml")

  def xmlNodmdSec =
    s"""
       |<mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:mods="http://www.loc.gov/mods/v3">
       |  <mods:recordIdentifier source="gbv-ppn">b30246039</mods:recordIdentifier>
       |</mets:mets>
       |""".stripMargin.toInputStream

  def xmlMultipleIds =
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
       |""".stripMargin.toInputStream

  def xmlNoLicense =
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
       |""".stripMargin.toInputStream

  def xmlMultipleLicense =
    s"""
       |<mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:mods="http://www.loc.gov/mods/v3">
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
       |""".stripMargin.toInputStream

  implicit class StringToInputStream(string: String) {
    def toInputStream = IOUtils.toInputStream(string, "UTF-8")
  }

}
