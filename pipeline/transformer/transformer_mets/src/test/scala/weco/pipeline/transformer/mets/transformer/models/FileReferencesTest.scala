package weco.pipeline.transformer.mets.transformer.models

import org.scalatest.{EitherValues, LoneElement}
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import weco.fixtures.LocalResources
import weco.pipeline.transformer.mets.generators.GoobiMetsGenerators
import weco.pipeline.transformer.mets.transformer.MetsXml

class FileReferencesTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with LocalResources
    with GoobiMetsGenerators
    with LoneElement {
  val xml = readResource("b30246039.xml")

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
        metsXmlWith(
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
        metsXmlWith(
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
        metsXmlWith(
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
        metsXmlWith(
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
        metsXmlWith(
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
        metsXmlWith(
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
        metsXmlWith(
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

}
