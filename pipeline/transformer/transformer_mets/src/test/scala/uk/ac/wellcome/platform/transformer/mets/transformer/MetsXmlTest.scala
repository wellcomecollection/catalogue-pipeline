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
    MetsXml(xmlRepeatedIdNodes).recordIdentifier shouldBe Right("b30246039")
  }

  it("does not parse if there is more than one distinct recordIdentifier") {
    MetsXml(xmlMultipleDistictIds).recordIdentifier shouldBe a[Left[_, _]]
  }

  it("parses accessConditionDz from XML") {
    MetsXml(xml).right.get.accessConditionDz shouldBe Right(Some("CC-BY-NC"))
  }

  it("parses accessConditionStatus from XML") {
    MetsXml(xml).right.get.accessConditionStatus shouldBe Right(Some("Open"))
  }

  it("parses accessConditionUsage from XML") {
    MetsXml(xml).right.get.accessConditionUsage shouldBe Right(
      Some("Some terms"))
  }

  it("gets the first accessConditionStatus if there are more than one") {
    val str = metsXmlWith(
      recordIdentifier = "b30246039",
      accessConditionStatus = Some("Open"),
      secondarySections =
        metsSecondarySection(accessConditionStatus = "Restricted"))
    MetsXml(str).getRight.accessConditionStatus shouldBe Right(Some("Open"))
  }

  it("parses a METS with no access condition") {
    MetsXml(xmlNoLicense).accessConditionDz shouldBe Right(None)
  }

  it("fails if the input string is not an xml") {
    MetsXml("hagdf") shouldBe a[Left[_, _]]
  }

  it("parse a METS with a repeated license node") {
    MetsXml(xmlRepeatedLicenseNode).accessConditionDz shouldBe Right(
      Some("CC-BY"))
  }

  it("does not parse a METS with multiple licenses") {
    MetsXml(xmlMultipleDistinctLicense).accessConditionDz shouldBe a[Left[_, _]]
  }

  it("parses thumbnail from XML") {
    MetsXml(xml).right.get.thumbnail("b30246039") shouldBe Some(
      FileReference(location="b30246039_0001.jp2", mimeType="image/jp2"))
  }

  it("parses first thumbnail when no ORDER attribute") {
    val str = metsXmlWith(
      recordIdentifier = "b30246039",
      fileSec = fileSec(filePrefix = "b30246039"),
      structMap = structMap)
    MetsXml(str).getRight.thumbnail("b30246039") shouldBe Some(
      FileReference("b30246039_0001.jp2","image/jp2"))
  }

  it("parses thumbnail using ORDER attrib when non-sequential order") {
    MetsXml(xmlNonSequentialOrder("b30246039")).getRight
      .thumbnail("b30246039") shouldBe Some(FileReference("b30246039_0001.jp2","image/jp2"))
  }

  it("parses thumbnail if filename doesn't start with bnumber") {
    val bnumber = "b30246039"
    val filePrefix = "V000012"
    MetsXml(
      metsXmlWith(
        recordIdentifier = bnumber,
        fileSec = fileSec(filePrefix),
        structMap = structMap)).getRight
      .thumbnail(bnumber) shouldBe Some(
      FileReference(s"${bnumber}_${filePrefix}_0001.jp2","image/jp2"))
  }

  it("parses thumbnail if filename starts with uppercase bnumber") {
    val bnumber = "b30246039"
    val filePrefix = bnumber.toUpperCase
    MetsXml(
      metsXmlWith(
        recordIdentifier = bnumber,
        fileSec = fileSec(filePrefix),
        structMap = structMap)).getRight
      .thumbnail(bnumber) shouldBe Some(FileReference(s"${filePrefix}_0001.jp2","image/jp2"))
  }

  it("cannot parse thumbnail when invalid file ID") {
    MetsXml(xmlInvalidFileId("b30246039")).getRight
      .thumbnail("b30246039") shouldBe None
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

  def xmlRepeatedIdNodes =
    <mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:mods="http://www.loc.gov/mods/v3">
      <mets:dmdSec ID="DMDLOG_0000">
        <mets:mdWrap MDTYPE="MODS">
          <mets:xmlData>
            <mods:mods>
              <mods:recordInfo>
                <mods:recordIdentifier source="gbv-ppn">b30246039</mods:recordIdentifier>
                <mods:recordIdentifier source="gbv-ppn">b30246039</mods:recordIdentifier>
              </mods:recordInfo>
            </mods:mods>
          </mets:xmlData>
        </mets:mdWrap>
      </mets:dmdSec>
    </mets:mets>

  def xmlMultipleDistictIds =
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

  def xmlNonSequentialOrder(recordIdentifier: String) =
    metsXmlWith(
      recordIdentifier,
      fileSec = fileSec(recordIdentifier),
      structMap = {
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
    metsXmlWith(
      recordIdentifier,
      fileSec = fileSec(recordIdentifier),
      structMap = {
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

  implicit class GetRight[T](b: Either[Throwable, T]) {
    def getRight = b.right.get
  }
}
