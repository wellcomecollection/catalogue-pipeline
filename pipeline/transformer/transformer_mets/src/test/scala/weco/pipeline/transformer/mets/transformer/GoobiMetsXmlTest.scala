package weco.pipeline.transformer.mets.transformer

import org.scalatest.{EitherValues, LoneElement}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.fixtures.LocalResources
import weco.pipeline.transformer.mets.generators.GoobiMetsGenerators
import weco.pipeline.transformer.mets.transformer.models.{
  FileReference,
  FileReferences,
  ThumbnailReference
}

class GoobiMetsXmlTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with LocalResources
    with GoobiMetsGenerators
    with LoneElement {

  val xml = readResource("b30246039.xml")

  it("parses recordIdentifier from XML") {
    MetsXml(xml).value.recordIdentifier shouldBe Right("b30246039")
  }

  it("does not parse a mets if recordIdentifier is outside of dmdSec element") {
    GoobiMetsXml(xmlNodmdSec).recordIdentifier shouldBe a[Left[_, _]]
  }

  it("does not parse if there is more than one recordIdentifier") {
    GoobiMetsXml(xmlRepeatedIdNodes).recordIdentifier shouldBe Right(
      "b30246039"
    )
  }

  it("does not parse if there is more than one distinct recordIdentifier") {
    GoobiMetsXml(xmlMultipleDistictIds).recordIdentifier shouldBe a[
      Left[_, _]
    ]
  }

  it("parses createdDate from XML") {
    MetsXml(xml).value.createdDate shouldBe Some("2018-06-26T23:45:51Z")
  }

  it("returns None for createdDate when CREATEDATE attribute is missing") {
    val xmlWithoutCreateDate = <mets:mets xmlns:mets="http://www.loc.gov/METS/">
      <mets:metsHdr>
        <mets:agent OTHERTYPE="SOFTWARE" ROLE="CREATOR" TYPE="OTHER">
          <mets:name>Goobi</mets:name>
        </mets:agent>
      </mets:metsHdr>
    </mets:mets>
    GoobiMetsXml(xmlWithoutCreateDate).createdDate shouldBe None
  }

  it("parses thumbnail from XML") {
    ThumbnailReference(
      MetsXml(
        xml
      ).value
    ).get.location shouldBe "objects/b30246039_0006.jp2"
  }

  it("only fetches one fileReference per file") {
    info(
      "according to the schema (https://www.loc.gov/standards/mets/mets.xsd)"
    )
    info(
      "each FLocat underneath a single file element should identify/contain identical copies of a single file."
    )
    info("we normally only have one <FLocat> per <file>.")
    FileReferences(
      MetsXml(
        goobiMetsXmlWith(
          recordIdentifier = "deadbeef",
          fileSec = <mets:fileSec><mets:fileGrp USE="OBJECTS">
        <mets:file ID="FILE_0001_OBJECTS" MIMETYPE="image/jp2">
          <mets:FLocat LOCTYPE="URL" xlink:href="objects/hello_0001.jp2"/>
          <mets:FLocat LOCTYPE="URL" xlink:href="objects/hello_0002.jp2"/>
        </mets:file>
      </mets:fileGrp></mets:fileSec>,
          structMap = structMap
        )
      ).value
    ) shouldBe List(
      FileReference(
        id = "FILE_0001_OBJECTS",
        location = "objects/hello_0001.jp2",
        listedMimeType = Some("image/jp2")
      )
    )
  }

  it("skips over unusable file definitions, but returns good ones") {
    FileReferences(
      MetsXml(
        goobiMetsXmlWith(
          recordIdentifier = "deadbeef",
          fileSec = <mets:fileSec><mets:fileGrp USE="OBJECTS">
        <mets:file ID="FILE_0001_OBJECTS" MIMETYPE="image/jp2">
        </mets:file>
        <mets:file ID="FILE_0002_OBJECTS" MIMETYPE="image/jp2">
          <mets:FLocat LOCTYPE="URL" xlink:href="objects/hello_0002.jp2"/>
        </mets:file>
      </mets:fileGrp></mets:fileSec>,
          structMap = structMap
        )
      ).value
    ) shouldBe List(
      FileReference(
        id = "FILE_0002_OBJECTS",
        location = "objects/hello_0002.jp2",
        listedMimeType = Some("image/jp2")
      )
    )
  }

  it("guesses the mime type if none is provided") {
    val reference = FileReferences(
      MetsXml(
        goobiMetsXmlWith(
          recordIdentifier = "deadbeef",
          fileSec = <mets:fileSec><mets:fileGrp USE="OBJECTS">
          <mets:file ID="FILE_0001_OBJECTS">
            <mets:FLocat LOCTYPE="URL" xlink:href="objects/hello_0001.pdf"/>
          </mets:file>
        </mets:fileGrp></mets:fileSec>,
          structMap = structMap
        )
      ).value
    ).loneElement

    reference shouldBe FileReference(
      id = "FILE_0001_OBJECTS",
      location = "objects/hello_0001.pdf",
      listedMimeType = None
    )
    reference.mimeType shouldBe Some("application/pdf")
  }

  it("treats an empty mimetype the same as an absent mimetype") {
    val reference = FileReferences(
      MetsXml(
        goobiMetsXmlWith(
          recordIdentifier = "deadbeef",
          fileSec = <mets:fileSec><mets:fileGrp USE="OBJECTS">
          <mets:file ID="FILE_0001_OBJECTS">
            <mets:FLocat LOCTYPE="URL" xlink:href="objects/hello_0001.pdf" MIMETYPE=""/>
          </mets:file>
        </mets:fileGrp></mets:fileSec>,
          structMap = structMap
        )
      ).value
    ).loneElement

    reference shouldBe FileReference(
      id = "FILE_0001_OBJECTS",
      location = "objects/hello_0001.pdf",
      listedMimeType = None
    )
    reference.mimeType shouldBe Some("application/pdf")
  }

  it("returns no mime type if it cannot be guessed") {
    val reference = FileReferences(
      MetsXml(
        goobiMetsXmlWith(
          recordIdentifier = "deadbeef",
          fileSec = <mets:fileSec><mets:fileGrp USE="OBJECTS">
          <mets:file ID="FILE_0001_OBJECTS">
            <mets:FLocat LOCTYPE="URL" xlink:href="objects/hello_0001.unknownextension"/>
          </mets:file>
        </mets:fileGrp></mets:fileSec>,
          structMap = structMap
        )
      ).value
    ).loneElement

    reference shouldBe FileReference(
      id = "FILE_0001_OBJECTS",
      location = "objects/hello_0001.unknownextension",
      listedMimeType = None
    )
    reference.mimeType shouldBe None
  }

  it("does not return a filereference if a file has no FLocat ") {
    FileReferences(
      MetsXml(
        goobiMetsXmlWith(
          recordIdentifier = "deadbeef",
          fileSec = <mets:fileSec><mets:fileGrp USE="OBJECTS">
        <mets:file ID="FILE_0001_OBJECTS" MIMETYPE="image/jp2">
        </mets:file>
      </mets:fileGrp></mets:fileSec>,
          structMap = structMap
        )
      ).value
    ) shouldBe Nil
  }

  it("does not return a filereference if an FLocat has no href") {
    FileReferences(
      MetsXml(
        goobiMetsXmlWith(
          recordIdentifier = "deadbeef",
          fileSec = <mets:fileSec><mets:fileGrp USE="OBJECTS">
          <mets:file ID="FILE_0001_OBJECTS" MIMETYPE="image/jp2">
            <mets:FLocat LOCTYPE="URL"/>
          </mets:file>
        </mets:fileGrp></mets:fileSec>,
          structMap = structMap
        )
      ).value
    ) shouldBe Nil
  }

  it("parses file references mapping from XML") {
    FileReferences(MetsXml(xml).value) shouldBe List(
      FileReference(
        id = "FILE_0001_OBJECTS",
        location = "objects/b30246039_0001.jp2",
        listedMimeType = Some("image/jp2")
      ),
      FileReference(
        id = "FILE_0002_OBJECTS",
        location = "objects/b30246039_0002.jp2",
        listedMimeType = Some("image/jp2")
      ),
      FileReference(
        id = "FILE_0003_OBJECTS",
        location = "objects/b30246039_0003.jp2",
        listedMimeType = Some("image/jp2")
      ),
      FileReference(
        id = "FILE_0004_OBJECTS",
        location = "objects/b30246039_0004.jp2",
        listedMimeType = Some("image/jp2")
      ),
      FileReference(
        id = "FILE_0005_OBJECTS",
        location = "objects/b30246039_0005.jp2",
        listedMimeType = Some("image/jp2")
      ),
      FileReference(
        id = "FILE_0006_OBJECTS",
        location = "objects/b30246039_0006.jp2",
        listedMimeType = Some("image/jp2")
      )
    )
  }

  it("parses first thumbnail when no ORDER attribute") {
    val str = goobiMetsXmlWith(
      recordIdentifier = "b30246039",
      fileSec = fileSec(filePrefix = "b30246039"),
      structMap = structMap
    )
    ThumbnailReference(
      MetsXml(
        str
      ).value
    ).get.location shouldBe "objects/b30246039_0001.jp2"
  }

  it("parses thumbnail using ORDER attrib when non-sequential order") {
    ThumbnailReference(
      MetsXml(
        xmlNonSequentialOrder("b30246039")
      ).value
    ).get.location shouldBe "objects/b30246039_0001.jp2"
  }

  it("cannot parse thumbnail when invalid file ID") {
    ThumbnailReference(
      MetsXml(
        xmlInvalidFileId("b30246039")
      ).value
    ) shouldBe None

  }

  it("parses first manifestation filename when present") {
    val xml = xmlWithManifestations(
      List(("LOG_0001", "01", "first.xml"), ("LOG_0002", "02", "second.xml"))
    )
    GoobiMetsXml(xml).firstManifestationFilename shouldBe Right("first.xml")
  }

  it("parses manifestation filename using ordering when present") {
    val xml = xmlWithManifestations(
      List(("LOG_0001", "02", "second.xml"), ("LOG_0002", "01", "first.xml"))
    )
    GoobiMetsXml(xml).firstManifestationFilename shouldBe Right("first.xml")
  }

  it("doesnt parse manifestation filename when not present") {
    val xml = xmlWithManifestations(Nil)
    GoobiMetsXml(xml).firstManifestationFilename shouldBe a[Left[_, _]]
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
    goobiMetsXmlWith(
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
    goobiMetsXmlWith(
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
