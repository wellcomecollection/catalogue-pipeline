package weco.pipeline.transformer.mets.transformer.models

import org.apache.commons.lang3.NotImplementedException
import org.scalatest.EitherValues
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import weco.fixtures.LocalResources
import weco.pipeline.transformer.mets.generators.GoobiMetsGenerators
import weco.pipeline.transformer.mets.transformer.MetsXml
import weco.pipeline.transformer.mets.transformers.MetsAccessConditions

import scala.xml.Elem

class ThumbnailReferenceTest
    extends AnyFunSpec
    with Matchers
    with EitherValues
    with LocalResources
    with GoobiMetsGenerators
    with TableDrivenPropertyChecks {

  describe("finding the file to be used for a thumbnail") {
    describe("given an explicit TitlePage") {
      it(
        "follows all the gratuitous indirection to find the actual href of a file"
      ) {
        val root =
          <mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:xlink="http://www.w3.org/1999/xlink">
            <mets:structMap TYPE="LOGICAL">
              <mets:div ADMID="AMD" DMDID="DMDLOG_0000" ID="LOG_0000" LABEL="La grande chirurgie ... composée l'an de grace 1363" TYPE="Monograph">
                <mets:div ID="LOG_0002" TYPE="TitlePage"/>
              </mets:div>
            </mets:structMap>
            <mets:structMap TYPE="PHYSICAL">
              <mets:div DMDID="DMDPHYS_0000" ID="PHYS_0000" TYPE="physSequence">
                <mets:div ADMID="AMD_0002" ID="PHYS_0002" ORDER="2" ORDERLABEL=" - " TYPE="page">
                  <mets:fptr FILEID="FILE_0002_OBJECTS"/>
                </mets:div>
              </mets:div>
            </mets:structMap>
            <mets:structLink>
              <mets:smLink xlink:from="LOG_0002" xlink:to="PHYS_0002"/>
            </mets:structLink>
            <mets:fileSec>
              <mets:fileGrp USE="OBJECTS">
                <mets:file ID="FILE_0002_OBJECTS" MIMETYPE="image/jp2">
                  <mets:FLocat LOCTYPE="URL" xlink:href="objects/b10915357_hin-wel-all-00001096_0002.jp2"/>
                </mets:file>
              </mets:fileGrp>
            </mets:fileSec>
          </mets:mets>

        ThumbnailReference(ThumbnailMetsXml(root)).get shouldBe FileReference(
          id = "FILE_0002_OBJECTS",
          location = "objects/b10915357_hin-wel-all-00001096_0002.jp2",
          listedMimeType = Some("image/jp2")
        )
      }
    }

    describe("deriving the thumbnail in the absence of a TitlePage") {

      it("chooses the first appropriate file, either an image or a pdf") {
        val examples = Table(
          ("file2Mime", "file2Href", "file3Mime", "file3Href"),
          ("image/jp2", "this one", "image/gif", "not this one"),
          ("application/pdf", "this one", "image/gif", "not this one"),
          ("image/gif", "this one", "application/pdf", "not this one"),
          ("font/ttf", "not this one", "application/pdf", "this one")
        )

        forAll(examples) {
          (file2Mime, file2Href, file3Mime, file3Href) =>
            val root =
              <mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:xlink="http://www.w3.org/1999/xlink">
                <mets:fileSec>
                  <mets:fileGrp USE="OBJECTS">
                    <mets:file ID="FILE_0001_OBJECTS" MIMETYPE="audio/midi">
                      <mets:FLocat LOCTYPE="URL" xlink:href="objects/eroica.midi"/>
                    </mets:file>
                    <mets:file ID="FILE_0002_OBJECTS" MIMETYPE={file2Mime}>
                      <mets:FLocat LOCTYPE="URL" xlink:href={file2Href}/>
                    </mets:file>
                    <mets:file ID="FILE_0003_OBJECTS" MIMETYPE={file3Mime}>
                      <mets:FLocat LOCTYPE="URL" xlink:href={file3Href}/>
                    </mets:file>
                  </mets:fileGrp>
                </mets:fileSec>
                <mets:structMap TYPE="PHYSICAL">
                  <mets:div DMDID="DMDPHYS_0000" ID="PHYS_0000" TYPE="physSequence">
                    <mets:div ADMID="AMD_0002" ID="PHYS_0001" ORDER="1" ORDERLABEL=" - " TYPE="page">
                      <mets:fptr FILEID="FILE_0001_OBJECTS"/>
                    </mets:div>
                    <mets:div ADMID="AMD_0002" ID="PHYS_0002" ORDER="2" ORDERLABEL=" - " TYPE="page">
                      <mets:fptr FILEID="FILE_0002_OBJECTS"/>
                    </mets:div>
                    <mets:div ADMID="AMD_0002" ID="PHYS_0003" ORDER="3" ORDERLABEL=" - " TYPE="page">
                      <mets:fptr FILEID="FILE_0003_OBJECTS"/>
                    </mets:div>
                  </mets:div>
                </mets:structMap>
              </mets:mets>

            ThumbnailReference(
              ThumbnailMetsXml(root)
            ).get.location shouldBe "this one"
        }

      }
    }

    describe("when there is no thumbnail") {
      it("returns None if the file in question has no href") {
        val root =
          <mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:xlink="http://www.w3.org/1999/xlink">
            <mets:structMap TYPE="LOGICAL">
              <mets:div ADMID="AMD" DMDID="DMDLOG_0000" ID="LOG_0000" LABEL="La grande chirurgie ... composée l'an de grace 1363" TYPE="Monograph">
                <mets:div ID="LOG_0002" TYPE="TitlePage"/>
              </mets:div>
            </mets:structMap>
            <mets:structMap TYPE="PHYSICAL">
              <mets:div DMDID="DMDPHYS_0000" ID="PHYS_0000" TYPE="physSequence">
                <mets:div ADMID="AMD_0002" ID="PHYS_0002" ORDER="2" ORDERLABEL=" - " TYPE="page">
                  <mets:fptr FILEID="FILE_0002_OBJECTS"/>
                </mets:div>
              </mets:div>
            </mets:structMap>
            <mets:structLink>
              <mets:smLink xlink:from="LOG_0002" xlink:to="PHYS_0002"/>
            </mets:structLink>
            <mets:fileSec>
              <mets:fileGrp USE="OBJECTS">
                <mets:file ID="FILE_0002_OBJECTS" MIMETYPE="image/jp2">
                  <mets:FLocat LOCTYPE="URL"/>
                </mets:file>
              </mets:fileGrp>
            </mets:fileSec>
          </mets:mets>

        ThumbnailReference(ThumbnailMetsXml(root)) shouldBe None
      }

      it("returns None if fileSec is missing") {
        val root =
          <mets:mets xmlns:mets="http://www.loc.gov/METS/" xmlns:xlink="http://www.w3.org/1999/xlink">
            <mets:structMap TYPE="LOGICAL">
              <mets:div ADMID="AMD" DMDID="DMDLOG_0000" ID="LOG_0000" LABEL="La grande chirurgie ... composée l'an de grace 1363" TYPE="Monograph">
                <mets:div ID="LOG_0002" TYPE="TitlePage"/>
              </mets:div>
            </mets:structMap>
            <mets:structMap TYPE="PHYSICAL">
              <mets:div DMDID="DMDPHYS_0000" ID="PHYS_0000" TYPE="physSequence">
                <mets:div ADMID="AMD_0002" ID="PHYS_0002" ORDER="2" ORDERLABEL=" - " TYPE="page">
                  <mets:fptr FILEID="FILE_0002_OBJECTS"/>
                </mets:div>
              </mets:div>
            </mets:structMap>
            <mets:structLink>
              <mets:smLink xlink:from="LOG_0002" xlink:to="PHYS_0002"/>
            </mets:structLink>

          </mets:mets>

        ThumbnailReference(ThumbnailMetsXml(root)) shouldBe None
      }
    }
  }
}

case class ThumbnailMetsXml(root: Elem) extends MetsXml {
  def firstManifestationFilename: Either[Exception, String] = Left(
    new NotImplementedException
  )
  def recordIdentifier: Either[Exception, String] = Left(
    new NotImplementedException
  )
  def accessConditions: Either[Throwable, MetsAccessConditions] = Left(
    new NotImplementedException
  )

}
