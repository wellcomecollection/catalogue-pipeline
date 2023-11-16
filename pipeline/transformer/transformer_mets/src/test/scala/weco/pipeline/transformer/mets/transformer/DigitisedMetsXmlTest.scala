package weco.pipeline.transformer.mets.transformer

import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.fixtures.LocalResources
import weco.pipeline.transformer.mets.generators.MetsGenerators
import weco.pipeline.transformer.mets.transformer.models.FileReference

class DigitisedMetsXmlTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with LocalResources
    with MetsGenerators {

  val xml = readResource("b30246039.xml")

  it("fails if the input string is not an xml") {
    MetsXml("hagdf") shouldBe a[Left[_, _]]
  }

  it("parses recordIdentifier from XML") {
    MetsXml(xml).value.recordIdentifier shouldBe Right("b30246039")
  }

  it("does not parse a mets if recordIdentifier is outside of dmdSec element") {
    DigitisedMetsXml(xmlNodmdSec).recordIdentifier shouldBe a[Left[_, _]]
  }

  it("does not parse if there is more than one recordIdentifier") {
    DigitisedMetsXml(xmlRepeatedIdNodes).recordIdentifier shouldBe Right(
      "b30246039"
    )
  }

  it("does not parse if there is more than one distinct recordIdentifier") {
    DigitisedMetsXml(xmlMultipleDistictIds).recordIdentifier shouldBe a[
      Left[_, _]
    ]
  }

  it("parses file references mapping from XML") {
    MetsXml(xml).value.fileReferencesMapping(
      "b30246039"
    ) shouldBe List(
      "PHYS_0001" -> FileReference(
        id = "FILE_0001_OBJECTS",
        location = "b30246039_0001.jp2",
        listedMimeType = Some("image/jp2")
      ),
      "PHYS_0002" -> FileReference(
        id = "FILE_0002_OBJECTS",
        location = "b30246039_0002.jp2",
        listedMimeType = Some("image/jp2")
      ),
      "PHYS_0003" -> FileReference(
        id = "FILE_0003_OBJECTS",
        location = "b30246039_0003.jp2",
        listedMimeType = Some("image/jp2")
      ),
      "PHYS_0004" -> FileReference(
        id = "FILE_0004_OBJECTS",
        location = "b30246039_0004.jp2",
        listedMimeType = Some("image/jp2")
      ),
      "PHYS_0005" -> FileReference(
        id = "FILE_0005_OBJECTS",
        location = "b30246039_0005.jp2",
        listedMimeType = Some("image/jp2")
      ),
      "PHYS_0006" -> FileReference(
        id = "FILE_0006_OBJECTS",
        location = "b30246039_0006.jp2",
        listedMimeType = Some("image/jp2")
      )
    )
  }

  it("parses title page ID from the XML when present") {
    MetsXml(xml).value.titlePageId shouldBe Some("PHYS_0006")
  }

  it("parses thumbnail from XML") {
    MetsXml(xml).value
      .fileReferencesMapping("b30246039")
      .head
      ._2
      .location shouldBe "b30246039_0001.jp2"
  }

  it("parses first thumbnail when no ORDER attribute") {
    val str = metsXmlWith(
      recordIdentifier = "b30246039",
      fileSec = fileSec(filePrefix = "b30246039"),
      structMap = structMap
    )
    MetsXml(str).value
      .fileReferencesMapping("b30246039")
      .head
      ._2
      .location shouldBe "b30246039_0001.jp2"
  }

  it("parses thumbnail using ORDER attrib when non-sequential order") {
    MetsXml(xmlNonSequentialOrder("b30246039")).value
      .fileReferencesMapping("b30246039")
      .head
      ._2
      .location shouldBe "b30246039_0001.jp2"
  }

  it("parses thumbnail if filename doesn't start with bnumber") {
    val bnumber = "b30246039"
    val filePrefix = "V000012"

    val metsXml =
      MetsXml(
        metsXmlWith(
          recordIdentifier = bnumber,
          fileSec = fileSec(filePrefix),
          structMap = structMap
        )
      ).value

    metsXml
      .fileReferencesMapping(bnumber)
      .head
      ._2
      .location shouldBe s"${bnumber}_${filePrefix}_0001.jp2"
  }

  it("parses thumbnail if filename starts with uppercase bnumber") {
    val bnumber = "b30246039"
    val filePrefix = bnumber.toUpperCase

    val metsXml =
      MetsXml(
        metsXmlWith(
          recordIdentifier = bnumber,
          fileSec = fileSec(filePrefix),
          structMap = structMap
        )
      ).value

    metsXml
      .fileReferencesMapping(bnumber)
      .head
      ._2
      .location shouldBe s"${filePrefix}_0001.jp2"
  }

  it("cannot parse thumbnail when invalid file ID") {
    MetsXml(xmlInvalidFileId("b30246039")).value
      .fileReferencesMapping("b30246039")
      .headOption shouldBe None
  }

  it("parses first manifestation filename when present") {
    val xml = xmlWithManifestations(
      List(("LOG_0001", "01", "first.xml"), ("LOG_0002", "02", "second.xml"))
    )
    DigitisedMetsXml(xml).firstManifestationFilename shouldBe Right("first.xml")
  }

  it("parses manifestation filename using ordering when present") {
    val xml = xmlWithManifestations(
      List(("LOG_0001", "02", "second.xml"), ("LOG_0002", "01", "first.xml"))
    )
    DigitisedMetsXml(xml).firstManifestationFilename shouldBe Right("first.xml")
  }

  it("doesnt parse manifestation filename when not present") {
    val xml = xmlWithManifestations(Nil)
    DigitisedMetsXml(xml).firstManifestationFilename shouldBe a[Left[_, _]]
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

  def xmlNonSequentialOrder(recordIdentifier: String) =
    metsXmlWith(
      recordIdentifier,
      fileSec = fileSec(recordIdentifier),
      structMap = <mets:structMap TYPE="PHYSICAL">
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
    )

  def xmlInvalidFileId(recordIdentifier: String) =
    metsXmlWith(
      recordIdentifier,
      fileSec = fileSec(recordIdentifier),
      structMap = <mets:structMap TYPE="PHYSICAL">
          <mets:div DMDID="DMDPHYS_0000" ID="PHYS_0000" TYPE="physSequence">
            <mets:div ADMID="AMD_0001" ID="PHYS_0001" ORDER="1" TYPE="page">
              <mets:fptr FILEID="OOPS" />
              <mets:fptr FILEID="FILE_0001_ALTO" />
            </mets:div>
            <mets:div ADMID="AMD_0002" ID="PHYS_0002" ORDER="2" TYPE="page">
              <mets:fptr FILEID="OH DEAR" />
            </mets:div>
          </mets:div>
        </mets:structMap>
    )
}
