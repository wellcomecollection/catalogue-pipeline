package weco.pipeline.transformer.mets.transformer

import weco.pipeline.transformer.mets.transformer.models.{
  FileReference,
  ThumbnailReference,
  XMLOps
}
import weco.pipeline.transformer.mets.transformers.{
  MetsAccessConditions,
  ModsAccessConditions
}

import scala.util.Try
import scala.xml.{Elem, XML}

trait MetsXml {
  val root: Elem
  val thumbnailReference: Option[FileReference]
  def firstManifestationFilename: Either[Exception, String]
  def fileReferences(bnumber: String): List[FileReference]
  def recordIdentifier: Either[Exception, String]

  def accessConditions: Either[Throwable, MetsAccessConditions]
}

case class GoobiMetsXml(root: Elem) extends MetsXml with XMLOps {

  /** The record identifier (generally the B number) is encoded in the METS. For
    * example:
    * {{{
    * <mets:dmdSec ID="DMDLOG_0000">
    *    <mets:mdWrap MDTYPE="MODS">
    *      <mets:xmlData>
    *       <mods:mods>
    *         <mods:recordInfo>
    *           <mods:recordIdentifier source="gbv-ppn">b30246039</mods:recordIdentifier>
    *         </mods:recordInfo>
    *       </mod:mods>
    *     </mets:xmlData>
    *   </mets:mdWrap>
    * </mets:dmdSec>
    * }}}
    * The expected output would be: "b30246039"
    */
  def recordIdentifier: Either[Exception, String] = {
    val identifierNodes =
      (root \\ "dmdSec" \ "mdWrap" \\ "recordInfo" \ "recordIdentifier").toList.distinct
    identifierNodes match {
      case Seq(node) => Right(node.text)
      case _ =>
        Left(new Exception("Could not parse recordIdentifier from METS XML"))
    }
  }

  /** Here we use the the items defined in the physicalStructMap to look up file
    * IDs in the (normalised) fileObjects mapping
    */
  def fileReferences(bnumber: String): List[FileReference] =
    physicalFileIds.flatMap {
      case fileId =>
        getFileReferences(fileId)
          .map(ref => normaliseLocation(bnumber, ref))
    }.toList

  /** Returns the first href to a manifestation in the logical structMap
    */
  def firstManifestationFilename: Either[Exception, String] = {
    (
      (root \ "structMap")
        .filterByAttribute("TYPE", "LOGICAL")
        .childrenWithTag("div")
        .filterByAttribute("TYPE", "MultipleManifestation")
        \ "div"
    )
      .sortByAttribute("ORDER")
      .headOption
      .map(_ \\ "mptr" \@ "{http://www.w3.org/1999/xlink}href") match {
      case Some(name) => Right(name)
      case None =>
        Left(
          new Exception("Could not parse any manifestation locations")
        )
    }
  }

  /** Valid METS documents should contain a physicalStructMap section, with the
    * bottom most divs each representing a physical page, and linking to files
    * in the corresponding fileSec structures:
    * {{{
    * <mets:structMap TYPE="PHYSICAL">
    *   <mets:div DMDID="DMDPHYS_0000" ID="PHYS_0000" TYPE="physSequence">
    *     <mets:div ADMID="AMD_0001" ID="PHYS_0001" ORDER="1" TYPE="page">
    *       <mets:fptr FILEID="FILE_0001_OBJECTS" />
    *       <mets:fptr FILEID="FILE_0001_ALTO" />
    *     </mets:div>
    *     <mets:div ADMID="AMD_0002" ID="PHYS_0002" ORDER="2" TYPE="page">
    *        <mets:fptr FILEID="FILE_0002_OBJECTS" />
    *        <mets:fptr FILEID="FILE_0002_ALTO" />
    *      </mets:div>
    *    </mets:div>
    *  </mets:structMap>
    * }}}
    * For this input we would expect the following output:
    *
    * Seq("PHYS_0001" -> "FILE_0001_OBJECTS", "PHYS_0002" ->
    * "FILE_0002_OBJECTS")
    */
  private def physicalFileIds: Seq[String] =
    ((root \ "structMap")
      .filterByAttribute("TYPE", "PHYSICAL")
      .descendentsWithTag("div")
      .sortByAttribute("ORDER") \ "fptr").map(_ \@ "FILEID")

  /** Filenames in DLCS are always prefixed with the bnumber (uppercase or
    * lowercase) to ensure uniqueness. However they might not be prefixed with
    * the bnumber in the METS file. So we need to do two things:
    *   - strip the "objects/" part of the location
    *   - prepend the bnumber followed by an underscore if it's not already
    *     present (uppercase or lowercase)
    */
  private def normaliseLocation(
    bnumber: String,
    fileReference: FileReference
  ): FileReference =
    fileReference.copy(
      location = fileReference.location.replaceFirst("objects/", "") match {
        case fileName if fileName.toLowerCase.startsWith(bnumber.toLowerCase) =>
          fileName
        case fileName => s"${bnumber}_$fileName"
      }
    )

  /** The METS XML contains locations of associated files, contained in a
    * mapping with the following format:
    *
    * {{{
    * <mets:fileSec>
    *   <mets:fileGrp USE="OBJECTS">
    *     <mets:file ID="FILE_0001_OBJECTS" MIMETYPE="image/jp2">
    *       <mets:FLocat LOCTYPE="URL" xlink:href="objects/b30246039_0001.jp2" />
    *     </mets:file>
    *     <mets:file ID="FILE_0002_OBJECTS" MIMETYPE="image/jp2">
    *       <mets:FLocat LOCTYPE="URL" xlink:href="objects/b30246039_0002.jp2" />
    *     </mets:file>
    *   </mets:fileGrp>
    * </mets:fileSec>
    * }}}
    * For the id "FILE_0002_OBJECTS", this function would return:
    * {{{
    *  FileReference("FILE_0002_OBJECTS", "objects/b30246039_0002.jp2", Some("image/jp2"))
    * }}}
    */
  private def getFileReferences(id: String): Seq[FileReference] =
    for {
      fileGrp <- root \ "fileSec" \ "fileGrp"
      objects <- fileGrp.find(_ \@ "USE" == "OBJECTS")
      listedFiles = objects \ "file"
      file <- listedFiles.find(_ \@ "ID" == id)
      objectHref <- (file \ "FLocat").headOption
        .map(_ \@ "{http://www.w3.org/1999/xlink}href")
      if objectHref.nonEmpty
    } yield FileReference(
      id,
      objectHref,
      Option(file \@ "MIMETYPE").filter(_.nonEmpty)
    )

  lazy val thumbnailReference: Option[FileReference] = ThumbnailReference(root)
  lazy val accessConditions: Either[Throwable, MetsAccessConditions] =
    ModsAccessConditions(root).parse
}

object MetsXml {
  def apply(root: Elem): MetsXml = {
    val agentName = (root \ "metsHdr" \ "agent" \ "name").text
    if (agentName.contains("Goobi")) {
      GoobiMetsXml(root)
    } else {
      throw new NotImplementedError(
        "Could not determine which flavour of METS to parse"
      )
    }
  }

  def apply(str: String): Either[Throwable, MetsXml] =
    Try(XML.loadString(str)).map(MetsXml(_)).toEither
}
