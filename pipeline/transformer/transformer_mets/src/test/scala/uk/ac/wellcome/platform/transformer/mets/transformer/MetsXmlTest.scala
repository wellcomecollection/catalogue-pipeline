package uk.ac.wellcome.platform.transformer.mets.transformer

import org.apache.commons.io.IOUtils
import org.scalatest.{FunSpec, Matchers}

import uk.ac.wellcome.platform.transformer.mets.fixtures.MetsGenerators

class MetsXmlTest extends FunSpec with Matchers with MetsGenerators {

  val xml = loadXmlFile("/b30246039.xml")

  it("parses recordIdentifier from XML") {
    MetsXml(xml).right.get.recordIdentifier shouldBe Right("b30246039")
  }

  it("does not parse a mets if recordIdentifier is outside of dmdSec element") {
    MetsXml(xmlNodmdSec).recordIdentifier shouldBe a[Left[_, _]]
  }

  it("does not parse if there is more than one recordIdentifier") {
    MetsXml(xmlMultipleIds).recordIdentifier shouldBe a[Left[_, _]]
  }

  it("parses accessCondition from XML") {
    MetsXml(xml).right.get.accessCondition shouldBe Right(Some("CC-BY-NC"))
  }

  it("parses a METS with no access condition") {
    MetsXml(xmlNoLicense).accessCondition shouldBe Right(None)
  }

  it("fails if the input string is not an xml") {
    MetsXml("hagdf") shouldBe a[Left[_, _]]
  }

  it("does not parse a METS with multiple licenses") {
    MetsXml(xmlMultipleLicense).accessCondition shouldBe a[Left[_, _]]
  }

  it("parses thumbnail from XML") {
    MetsXml(xml).right.get.thumbnailLocation("b30246039") shouldBe Some("b30246039_0001.jp2")
  }

  it("parses first thumbnail when no ORDER attribute") {
    MetsXml(xmlWithThumbnailImages("b30246039")).thumbnailLocation("b30246039") shouldBe Some(
      "b30246039_0001.jp2")
  }

  it("parses thumbnail using ORDER attrib when non-sequential order") {
    MetsXml(xmlNonSequentialOrder("b30246039")).thumbnailLocation("b30246039") shouldBe Some(
      "b30246039_0001.jp2")
  }

  it("parses thumbnail if filename doesn't start with bnumber") {
    val bnumber = "b30246039"
    val filePrefix = "V000012"
    MetsXml(
      xmlWithThumbnailImages(
        recordIdentifier = bnumber,
        filePrefix = _ => filePrefix)).thumbnailLocation(bnumber) shouldBe Some(
      s"${bnumber}_${filePrefix}_0001.jp2")
  }

  it("cannot parse thumbnail when invalid file ID") {
    MetsXml(xmlInvalidFileId("b30246039")).thumbnailLocation("b30246039") shouldBe None
  }

  it("parses first manifestation filename when present") {
    val xml = xmlWithManifestations(
      List(("LOG_0001", "01", "first.xml"), ("LOG_0002", "02", "second.xml"))
    )
    MetsXml(xml).firstManifestationFilename shouldBe Right("first.xml")
  }

  it("parses manifestation filename using ordering when present") {
    val xml = xmlWithManifestations(
      List(("LOG_0001", "02", "second.xml"), ("LOG_0002", "01", "first.xml"))
    )
    MetsXml(xml).firstManifestationFilename shouldBe Right("first.xml")
  }

  it("doesnt parse manifestation filename when not present") {
    val xml = xmlWithManifestations(Nil)
    MetsXml(xml).firstManifestationFilename shouldBe a[Left[_, _]]
  }

  def xmlNodmdSec =
    <mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:mods="http://www.loc.gov/mods/v3">
       <mods:recordIdentifier source="gbv-ppn">b30246039</mods:recordIdentifier>
     </mets:mets>

  def xmlMultipleIds =
    <mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:mods="http://www.loc.gov/mods/v3">
      <mets:dmdSec ID="DMDLOG_0000">
        <mets:mdWrap MDTYPE="MODS">
          <mets:xmlData>
            <mods:mods>
              <mods:recordInfo>
                <mods:recordIdentifier source="gbv-ppn">b30246039</mods:recordIdentifier>
                <mods:recordIdentifier source="gbv-ppn">b3024346567</mods:recordIdentifier>
              </mods:recordInfo>
            </mods:mods>
          </mets:xmlData>
        </mets:mdWrap>
      </mets:dmdSec>
    </mets:mets>

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

  def xmlMultipleLicense =
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

  def xmlNonSequentialOrder(recordIdentifier: String) =
    xmlWithThumbnailImages(
      recordIdentifier, {
        <mets:structMap TYPE="PHYSICAL">
        <mets:div DMDID="DMDPHYS_0000" ID="PHYS_0000" TYPE="physSequence">
          <mets:div ADMID="AMD_0002" ID="PHYS_0002" ORDER="2" TYPE="page">
            <mets:fptr FILEID="FILE_0002_OBJECTS" />
          </mets:div>
          <mets:div ADMID="AMD_0001" ID="PHYS_0001" ORDER="1" TYPE="page">
            <mets:fptr FILEID="FILE_0001_OBJECTS" />
            <mets:fptr FILEID="FILE_0001_ALTO" />
          </mets:div>
        </mets:div>
      </mets:structMap>
      }
    )

  def xmlInvalidFileId(recordIdentifier: String) =
    xmlWithThumbnailImages(
      recordIdentifier, {
        <mets:structMap TYPE="PHYSICAL">
        <mets:div DMDID="DMDPHYS_0000" ID="PHYS_0000" TYPE="physSequence">
          <mets:div ADMID="AMD_0001" ID="PHYS_0001" ORDER="1" TYPE="page">
            <mets:fptr FILEID="OOPS" />
            <mets:fptr FILEID="FILE_0001_ALTO" />
          </mets:div>
          <mets:div ADMID="AMD_0002" ID="PHYS_0002" ORDER="2" TYPE="page">
            <mets:fptr FILEID="FILE_0002_OBJECTS" />
          </mets:div>
        </mets:div>
      </mets:structMap>
      }
    )

  def loadXmlFile(path: String) =
    IOUtils.toString(getClass.getResourceAsStream(path), "UTF-8")
}
