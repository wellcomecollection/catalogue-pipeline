package uk.ac.wellcome.platform.transformer.mets.transformer

import scala.util.Try
import scala.collection.immutable.ListMap
import scala.xml.{Elem, NodeSeq, XML}

case class MetsXml(root: Elem) {

  /** The record identifier (generally the B number) is encoded in the METS. For
    *  example:
    *
    *  <mets:dmdSec ID="DMDLOG_0000">
    *    <mets:mdWrap MDTYPE="MODS">
    *      <mets:xmlData>
    *        <mods:mods>
    *          <mods:recordInfo>
    *            <mods:recordIdentifier source="gbv-ppn">b30246039</mods:recordIdentifier>
    *          </mods:recordInfo>
    *        </mod:mods>
    *      </mets:xmlData>
    *    </mets:mdWrap>
    *  </mets:dmdSec>
    *
    *  The expected output would be: "b30246039"
    */
  def recordIdentifier: Either[Exception, String] = {
    val identifierNodes =
      (root \\ "dmdSec" \ "mdWrap" \\ "recordInfo" \ "recordIdentifier").toList
    identifierNodes match {
      case List(identifierNode) => Right[Exception, String](identifierNode.text)
      case _ =>
        Left[Exception, String](
          new Exception("Could not parse recordIdentifier from METS XML"))
    }
  }

  /** For licenses We are interested with the access condition with type `dz`.
    *  For example:
    *
    *  <mets:dmdSec ID="DMDLOG_0000">
    *    <mets:mdWrap MDTYPE="MODS">
    *      <mets:xmlData>
    *        <mods:mods>
    *          ...
    *          <mods:accessCondition type="dz">CC-BY-NC</mods:accessCondition>
    *          <mods:accessCondition type="player">63</mods:accessCondition>
    *          <mods:accessCondition type="status">Open</mods:accessCondition>
    *          ...
    *        </mods:mods>
    *      </mets:xmlData>
    *    </mets:mdWrap>
    *  </mets:dmdSec>
    *
    *  The expected output would be: "CC-BY-NC"
    */
  def accessConditionDz: Either[Exception, Option[String]] = {
    val licenseNodes = (root \\ "dmdSec" \ "mdWrap" \\ "accessCondition")
      .filterByAttribute("type", "dz")
      .toList
    licenseNodes match {
      case Nil               => Right(None)
      case List(licenseNode) => Right(Some(licenseNode.text))
      case _ =>
        Left(
          new Exception("Found multiple accessCondtions with type dz in METS XML")
        )
    }
  }

  /** Here we use the first defined item in the physicalStructMap to lookup a
    *  file ID the fileObjects mapping, and use the files location as the
    *  thumbnail image.
    */
  def thumbnailLocation(bnumber: String): Option[String] = {
    // Filenames in DLCS are always prefixed with the bnumber (uppercase or lowercase) to ensure uniqueness.
    // However they might not be prefixed with the bnumber in the METS file.
    // So we need to do two things:
    //  - strip the "objects/" part of the link
    //  - prepend the bnumber followed by an underscore if it's not already present (uppercase or lowercase)
    val filePrefixRegex = s"""objects/(?i:($bnumber)_)?(.*)""".r
    physicalStructMap.headOption
      .flatMap { case (_, fileId) => fileObjects.get(fileId) }
      .map { fileUrl =>
        fileUrl match {
          case filePrefixRegex(caseInsensitiveBnumber, postFix) =>
            Option(caseInsensitiveBnumber) match {
              case Some(caseInsensitiveBnumber) =>
                s"${caseInsensitiveBnumber}_$postFix"
              case _ => s"${bnumber}_$postFix"
            }
          case _ => fileUrl
        }
      }
  }

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

  /** The METS XML contains locations of associated files, contained in a
    *  mapping with the following format:
    *
    *  <mets:fileSec>
    *    <mets:fileGrp USE="OBJECTS">
    *      <mets:file ID="FILE_0001_OBJECTS" MIMETYPE="image/jp2">
    *        <mets:FLocat LOCTYPE="URL" xlink:href="objects/b30246039_0001.jp2" />
    *      </mets:file>
    *      <mets:file ID="FILE_0002_OBJECTS" MIMETYPE="image/jp2">
    *        <mets:FLocat LOCTYPE="URL" xlink:href="objects/b30246039_0002.jp2" />
    *      </mets:file>
    *    </mets:fileGrp>
    *  </mets:fileSec>
    *
    *  For this input we would expect the following output:
    *
    *  Map("FILE_0001_OBJECTS" -> "objects/b30246039_0001.jp2",
    *      "FILE_0002_OBJECTS" -> "objects/b30246039_0002.jp2")
    */
  private def fileObjects: Map[String, String] =
    (root \ "fileSec" \ "fileGrp")
      .filterByAttribute("USE", "OBJECTS")
      .childrenWithTag("file")
      .toMapping(
        keyAttrib = "ID",
        valueNode = "FLocat",
        valueAttrib = "{http://www.w3.org/1999/xlink}href"
      )

  /** Valid METS documents should contain a physicalStructMap section, with the
    *  bottom most divs each representing a physical page, and linking to files
    *  in the corresponding fileSec structures:
    *
    *  <mets:structMap TYPE="PHYSICAL">
    *    <mets:div DMDID="DMDPHYS_0000" ID="PHYS_0000" TYPE="physSequence">
    *      <mets:div ADMID="AMD_0001" ID="PHYS_0001" ORDER="1" TYPE="page">
    *        <mets:fptr FILEID="FILE_0001_OBJECTS" />
    *        <mets:fptr FILEID="FILE_0001_ALTO" />
    *      </mets:div>
    *      <mets:div ADMID="AMD_0002" ID="PHYS_0002" ORDER="2" TYPE="page">
    *        <mets:fptr FILEID="FILE_0002_OBJECTS" />
    *        <mets:fptr FILEID="FILE_0002_ALTO" />
    *      </mets:div>
    *    </mets:div>
    *  </mets:structMap>
    *
    *  For this input we would expect the following output:
    *
    *  Map("PHYS_0001" -> "FILE_0001_OBJECTS",
    *      "PHYS_0002" -> "FILE_0002_OBJECTS")
    */
  private def physicalStructMap: ListMap[String, String] =
    (root \ "structMap")
      .filterByAttribute("TYPE", "PHYSICAL")
      .descendentsWithTag("div")
      .sortByAttribute("ORDER")
      .toMapping(
        keyAttrib = "ID",
        valueNode = "fptr",
        valueAttrib = "FILEID"
      )

  /** Valid METS documents should contain a logicalStructMap section. When this
    *  is data containing multiple manifestations, we can expect the map to
    *  include links to the other XML files:
    *
    *  <mets:structMap TYPE="LOGICAL">
    *    <mets:div ADMID="AMD" DMDID="DMDLOG_0000" ID="LOG_0000" TYPE="MultipleManifestation">
    *      <mets:div ID="LOG_0001" ORDER="01" TYPE="Monograph">
    *        <mets:mptr LOCTYPE="URL" xlink:href="b22012692_0001.xml" />
    *      </mets:div>
    *      <mets:div ID="LOG_0002" ORDER="03" TYPE="Monograph">
    *        <mets:mptr LOCTYPE="URL" xlink:href="b22012692_0003.xml" />
    *      </mets:div>
    *    </mets:div>
    *  </mets:structMap>
    *
    *  For this input we would expect the following output:
    *
    *  Map("LOG_0000" -> "b22012692_0001.xml",
    *      "LOG_0002" -> "b22012692_0003.xml")
    */
  private def logicalStructMapForMultipleManifestations
    : ListMap[String, String] =
    (root \ "structMap")
      .filterByAttribute("TYPE", "LOGICAL")
      .childrenWithTag("div")
      .filterByAttribute("TYPE", "MultipleManifestation")
      .descendentsWithTag("div")
      .sortByAttribute("ORDER")
      .toMapping(
        keyAttrib = "ID",
        valueNode = "mptr",
        valueAttrib = "{http://www.w3.org/1999/xlink}href"
      )

  implicit class NodeSeqOps(nodes: NodeSeq) {

    def filterByAttribute(attrib: String, value: String) =
      nodes.filter(_ \@ attrib == value)

    def childrenWithTag(tag: String) =
      nodes.flatMap(_ \ tag)

    def descendentsWithTag(tag: String) =
      nodes.flatMap(_ \\ tag)

    def sortByAttribute(attrib: String) =
      nodes.sortBy(_ \@ attrib)

    def toMapping(keyAttrib: String, valueNode: String, valueAttrib: String) = {
      val mappings = nodes
        .map { node =>
          val key = node \@ keyAttrib
          val value = (node \ valueNode).toList.headOption.map(_ \@ valueAttrib)
          (key, value)
        }
        .collect {
          case (key, Some(value)) if key.nonEmpty && value.nonEmpty =>
            (key, value)
        }
      // Return a ListMap here over standard Map to preserve ordering
      ListMap(mappings: _*)
    }
  }
}

object MetsXml {

  def apply(str: String): Either[Throwable, MetsXml] =
    Try(XML.loadString(str)).map(MetsXml(_)).toEither
}
