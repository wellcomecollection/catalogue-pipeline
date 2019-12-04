package uk.ac.wellcome.platform.transformer.mets.parsers

import org.apache.commons.io.IOUtils
import org.scalatest.{FunSpec, Matchers}
import scala.xml.Elem

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

  it("fails if the input string is not an xml") {
    MetsXmlParser("hagdf") shouldBe a[Left[_, _]]
  }

  it("parses thumbnail from XML") {
    MetsXmlParser(xml).right.get.thumbnailLocation shouldBe Some(
      "b30246039_0001.jp2")
  }

  it("parses first thumbnail when no ORDER attribute") {
    MetsXmlParser(xmlNoOrderAttrib).right.get.thumbnailLocation shouldBe Some(
      "b30246039_0001.jp2")
  }

  it("parses thumbnail using ORDER attrib when non-sequential order") {
    MetsXmlParser(xmlNonSequentialOrder).right.get.thumbnailLocation shouldBe Some(
      "b30246039_0001.jp2")
  }

  it("cannot parse thumbnail when invalid file ID") {
    MetsXmlParser(xmlInvalidFileId).right.get.thumbnailLocation shouldBe None
  }

  def xml =
    IOUtils.toString(getClass.getResourceAsStream("/b30246039.xml"), "UTF-8")

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

  def xmlWithThumbnailImages(structMap: Elem) =
    <mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:mods="http://www.loc.gov/mods/v3" xmlns:xlink="http://www.w3.org/1999/xlink">
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
      <mets:fileSec>
        <mets:fileGrp USE="OBJECTS">
          <mets:file ID="FILE_0001_OBJECTS" MIMETYPE="image/jp2">
            <mets:FLocat LOCTYPE="URL" xlink:href="objects/b30246039_0001.jp2" />
          </mets:file>
          <mets:file ID="FILE_0002_OBJECTS" MIMETYPE="image/jp2">
            <mets:FLocat LOCTYPE="URL" xlink:href="objects/b30246039_0002.jp2" />
          </mets:file>
        </mets:fileGrp>
        <mets:fileGrp USE="ALTO">
          <mets:file ID="FILE_0001_ALTO" MIMETYPE="application/xml">
            <mets:FLocat LOCTYPE="URL" xlink:href="alto/b30246039_0001.xml" />
          </mets:file>
        </mets:fileGrp>
      </mets:fileSec>
      {structMap}
    </mets:mets>

  def xmlNoOrderAttrib =
    xmlWithThumbnailImages {
      <mets:structMap TYPE="PHYSICAL">
        <mets:div DMDID="DMDPHYS_0000" ID="PHYS_0000" TYPE="physSequence">
          <mets:div ADMID="AMD_0001" ID="PHYS_0001" TYPE="page">
            <mets:fptr FILEID="FILE_0001_OBJECTS" />
            <mets:fptr FILEID="FILE_0001_ALTO" />
          </mets:div>
          <mets:div ADMID="AMD_0002" ID="PHYS_0002" TYPE="page">
            <mets:fptr FILEID="FILE_0002_OBJECTS" />
          </mets:div>
        </mets:div>
      </mets:structMap>
    }

  def xmlNonSequentialOrder =
    xmlWithThumbnailImages {
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

  def xmlInvalidFileId =
    xmlWithThumbnailImages {
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
}
