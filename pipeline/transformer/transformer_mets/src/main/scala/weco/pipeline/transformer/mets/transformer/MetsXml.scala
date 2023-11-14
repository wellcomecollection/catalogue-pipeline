package weco.pipeline.transformer.mets.transformer

import weco.pipeline.transformer.mets.transformer.models.{FileReference, XMLOps}

import scala.util.Try
import scala.xml.{Elem, XML}

case class MetsXml(root: Elem) extends XMLOps {

  /** The record identifier (generally the B number) is encoded in the METS. For
    * example:
    *
    * <mets:dmdSec ID="DMDLOG_0000"> <mets:mdWrap MDTYPE="MODS"> <mets:xmlData>
    * <mods:mods> <mods:recordInfo> <mods:recordIdentifier
    * source="gbv-ppn">b30246039</mods:recordIdentifier> </mods:recordInfo>
    * </mod:mods> </mets:xmlData> </mets:mdWrap> </mets:dmdSec>
    *
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
  def fileReferencesMapping(bnumber: String): List[(String, FileReference)] =
    physicalFileIdsMapping.flatMap {
      case (id, fileId) =>
        getFileReferences(fileId)
          .map(ref => (id, normaliseLocation(bnumber, ref)))
    }.toList

  /** Returns the first href to a manifestation in the logical structMap
    */
  def firstManifestationFilename: Either[Exception, String] =
    logicalStructMapForMultipleManifestations.headOption match {
      case Some((_, name)) => Right(name)
      case None =>
        Left(
          new Exception("Could not parse any manifestation locations")
        )
    }

  /** Returns the physical ID for the TitlePage element if it exists. */
  def titlePageId: Option[String] =
    logicalStructMapForType
      .collectFirst { case (id, "TitlePage") => id }
      .flatMap(smLink(_))

  /** Valid METS documents should contain a physicalStructMap section, with the
    * bottom most divs each representing a physical page, and linking to files
    * in the corresponding fileSec structures:
    *
    * <mets:structMap TYPE="PHYSICAL"> <mets:div DMDID="DMDPHYS_0000"
    * ID="PHYS_0000" TYPE="physSequence"> <mets:div ADMID="AMD_0001"
    * ID="PHYS_0001" ORDER="1" TYPE="page"> <mets:fptr
    * FILEID="FILE_0001_OBJECTS" /> <mets:fptr FILEID="FILE_0001_ALTO" />
    * </mets:div> <mets:div ADMID="AMD_0002" ID="PHYS_0002" ORDER="2"
    * TYPE="page"> <mets:fptr FILEID="FILE_0002_OBJECTS" /> <mets:fptr
    * FILEID="FILE_0002_ALTO" /> </mets:div> </mets:div> </mets:structMap>
    *
    * For this input we would expect the following output:
    *
    * Seq("PHYS_0001" -> "FILE_0001_OBJECTS", "PHYS_0002" ->
    * "FILE_0002_OBJECTS")
    */
  private def physicalFileIdsMapping: Seq[(String, String)] =
    (root \ "structMap")
      .filterByAttribute("TYPE", "PHYSICAL")
      .descendentsWithTag("div")
      .sortByAttribute("ORDER")
      .toMapping(
        keyAttrib = "ID",
        valueNode = Some("fptr"),
        valueAttrib = "FILEID"
      )

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
    * <mets:fileSec> <mets:fileGrp USE="OBJECTS"> <mets:file
    * ID="FILE_0001_OBJECTS" MIMETYPE="image/jp2"> <mets:FLocat LOCTYPE="URL"
    * xlink:href="objects/b30246039_0001.jp2" /> </mets:file> <mets:file
    * ID="FILE_0002_OBJECTS" MIMETYPE="image/jp2"> <mets:FLocat LOCTYPE="URL"
    * xlink:href="objects/b30246039_0002.jp2" /> </mets:file> </mets:fileGrp>
    * </mets:fileSec>
    *
    * For the id "FILE_0002_OBJECTS", this function would return:
    * FileReference("FILE_0002_OBJECTS", "objects/b30246039_0002.jp2",
    * Some("image/jp2"))
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

  /** Valid METS documents should contain a LOGICAL structMap section. When this
    * is data containing multiple manifestations, we can expect the map to
    * include links to the other XML files:
    *
    * <mets:structMap TYPE="LOGICAL"> <mets:div ADMID="AMD" DMDID="DMDLOG_0000"
    * ID="LOG_0000" TYPE="MultipleManifestation"> <mets:div ID="LOG_0001"
    * ORDER="01" TYPE="Monograph"> <mets:mptr LOCTYPE="URL"
    * xlink:href="b22012692_0001.xml" /> </mets:div> <mets:div ID="LOG_0002"
    * ORDER="03" TYPE="Monograph"> <mets:mptr LOCTYPE="URL"
    * xlink:href="b22012692_0003.xml" /> </mets:div> </mets:div>
    * </mets:structMap>
    *
    * For this input we would expect the following output:
    *
    * Seq("LOG_0000" -> "b22012692_0001.xml", "LOG_0002" ->
    * "b22012692_0003.xml")
    */
  private def logicalStructMapForMultipleManifestations: Seq[(String, String)] =
    (root \ "structMap")
      .filterByAttribute("TYPE", "LOGICAL")
      .childrenWithTag("div")
      .filterByAttribute("TYPE", "MultipleManifestation")
      .descendentsWithTag("div")
      .sortByAttribute("ORDER")
      .toMapping(
        keyAttrib = "ID",
        valueNode = Some("mptr"),
        valueAttrib = "{http://www.w3.org/1999/xlink}href"
      )

  /** Valid METS documents should contain a LOGICAL structMap section, with
    * descendent divs containing an ID and a TYPE attribute:
    *
    * <mets:structMap TYPE="LOGICAL"> <mets:div ADMID="AMD" DMDID="DMDLOG_0000"
    * ID="LOG_0000" LABEL="[Report 1942] /" TYPE="Monograph"> <mets:div
    * ID="LOG_0001" TYPE="Cover" /> <mets:div ID="LOG_0002" TYPE="TitlePage" />
    * </mets:div> </mets:structMap>
    *
    * For this input we would expect the following output:
    *
    * Seq("LOG_0000" -> "Monographl", "LOG_0001" -> "Cover") "LOG_0002" ->
    * "TitlePage")
    */
  private def logicalStructMapForType: Seq[(String, String)] =
    (root \ "structMap")
      .filterByAttribute("TYPE", "LOGICAL")
      .descendentsWithTag("div")
      .toMapping(
        keyAttrib = "ID",
        valueAttrib = "TYPE"
      )

  /** The structLink sections maps the logical and physical IDs represented in
    * the document:
    * {{{
    * <mets:structLink>
    *   <mets:smLink xlink:from="LOG_0000" xlink:to="PHYS_0001" />
    *   <mets:smLink xlink:from="LOG_0000" xlink:to="PHYS_0002" />
    *   <mets:smLink xlink:from="LOG_0001" xlink:to="PHYS_0001" />
    *   <mets:smLink xlink:from="LOG_0002" xlink:to="PHYS_0003" />
    * </mets:structLink>
    * }}}
    * For this input we would expect smLink("LOG_0001") to return "PHYS_001"
    */
  private def smLink(from: String): Option[String] = {
    (root \ "structLink" \ "smLink")
      .filterByAttribute("{http://www.w3.org/1999/xlink}from", from)
      .headOption
      .map(_ \@ "{http://www.w3.org/1999/xlink}to")
  }
}

object MetsXml {

  def apply(str: String): Either[Throwable, MetsXml] =
    Try(XML.loadString(str)).map(MetsXml(_)).toEither
}
