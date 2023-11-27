package weco.pipeline.transformer.mets.transformers

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.fixtures.LocalResources
import weco.pipeline.transformer.mets.generators.MetsGenerators
import weco.pipeline.transformer.mets.transformer.MetsXml

class ModsAccessConditionsTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with LocalResources
    with MetsGenerators {
  val xml = MetsXml(readResource("b30246039.xml")).value.root

  it("parses accessConditionDz from XML") {
    ModsAccessConditions(xml).dz shouldBe
      Some("CC-BY-NC")

  }

  it("parses accessConditionStatus from XML") {
    ModsAccessConditions(xml).status shouldBe
      Some("Open")

  }

  it("parses accessConditionUsage from XML") {
    ModsAccessConditions(xml).usage shouldBe Some("Some terms")
  }

  it("gets the first accessConditionStatus if there are more than one") {
    val str = metsXmlWith(
      recordIdentifier = "b30246039",
      accessConditionStatus = Some("Open"),
      secondarySections =
        metsSecondarySection(accessConditionStatus = "Restricted")
    )
    ModsAccessConditions(MetsXml(str).value.root).status shouldBe
      Some("Open")

  }

  it("parses a METS with no access condition") {
    ModsAccessConditions(xmlNoLicense).dz shouldBe None
  }

  it("parse a METS with a repeated license node") {
    ModsAccessConditions(xmlRepeatedLicenseNode).dz shouldBe Some("CC-BY")
  }

  it("finds the first licence in document order") {
    ModsAccessConditions(xmlMultipleDistinctLicense).dz shouldBe Some(
      "CC-BY-NC"
    )
  }

  def xmlNoLicense =
    <mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:mods="http://www.loc.gov/mods/v3">
      <mets:dmdSec ID="DMDLOG_0000">
        <mets:mdWrap MDTYPE="MODS">
          <mets:xmlData>
            <mods:mods>
              <mods:recordInfo>
                <mods:recordIdentifier source="gbv-ppn">b30246039</mods:recordIdentifier>
              </mods:recordInfo>
            </mods:mods>
          </mets:xmlData>
        </mets:mdWrap>
      </mets:dmdSec>
    </mets:mets>

  def xmlMultipleDistinctLicense =
    <mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:mods="http://www.loc.gov/mods/v3">
      <mets:dmdSec ID="DMDLOG_0000">
        <mets:mdWrap MDTYPE="MODS">
          <mets:xmlData>
            <mods:mods>
              <mods:recordInfo>
                <mods:recordIdentifier source="gbv-ppn">b30246039</mods:recordIdentifier>
              </mods:recordInfo>
              <mods:accessCondition type="dz">CC-BY-NC</mods:accessCondition>
              <mods:accessCondition type="dz">CC-BY</mods:accessCondition>
            </mods:mods>
          </mets:xmlData>
        </mets:mdWrap>
      </mets:dmdSec>
    </mets:mets>

  def xmlRepeatedLicenseNode =
    <mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:mods="http://www.loc.gov/mods/v3">
      <mets:dmdSec ID="DMDLOG_0000">
        <mets:mdWrap MDTYPE="MODS">
          <mets:xmlData>
            <mods:mods>
              <mods:recordInfo>
                <mods:recordIdentifier source="gbv-ppn">b30246039</mods:recordIdentifier>
              </mods:recordInfo>
              <mods:accessCondition type="dz">CC-BY</mods:accessCondition>
              <mods:accessCondition type="dz">CC-BY</mods:accessCondition>
            </mods:mods>
          </mets:xmlData>
        </mets:mdWrap>
      </mets:dmdSec>
    </mets:mets>

}
