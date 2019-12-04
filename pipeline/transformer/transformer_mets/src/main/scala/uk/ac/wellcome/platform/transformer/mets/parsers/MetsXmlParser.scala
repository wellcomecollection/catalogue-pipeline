package uk.ac.wellcome.platform.transformer.mets.parsers

import scala.collection.immutable.ListMap
import scala.util.Try
import scala.xml.{Elem, NodeSeq, XML}

import uk.ac.wellcome.platform.transformer.mets.transformer.Mets

object MetsXmlParser {

  def apply(str: String): Either[Throwable, Mets] =
    for {
      is <- Try(XML.loadString(str)).toEither
      mets <- MetsXmlParser(is)
    } yield (mets)

  def apply(root: Elem): Either[Exception, Mets] = {
    for {
      id <- recordIdentifier(root)
      maybeAccessCondition <- accessCondition(root)
    } yield
      Mets(
        recordIdentifier = id,
        accessCondition = maybeAccessCondition,
        thumbnailLocation = thumbnailLocation(root),
      )
  }

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
  private def recordIdentifier(root: Elem): Either[Exception, String] = {
    val identifierNodes =
      (root \\ "dmdSec" \ "mdWrap" \\ "recordInfo" \ "recordIdentifier").toList
    identifierNodes match {
      case List(identifierNode) => Right[Exception, String](identifierNode.text)
      case _ =>
        Left[Exception, String](
          new Exception("Could not parse recordIdentifier from METS XML"))
    }
  }

  /** We are interested with the access condition with type `dz`. For example:
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
  private def accessCondition(root: Elem): Either[Exception, Option[String]] = {
    val licenseNodes = (root \\ "dmdSec" \ "mdWrap" \\ "accessCondition")
      .filterByAttribute("type", "dz")
      .toList
    licenseNodes match {
      case Nil => Right(None)
      case List(licenseNode) =>
        Right[Exception, Option[String]](Some(licenseNode.text))
      case _ =>
        Left[Exception, Option[String]](
          new Exception("Found multiple accessCondtions in METS XML"))
    }
  }

  /** Here we use the first defined item in the physicalStructMap to lookup a
    *  file ID the fileObjects mapping, and use the files location as the
    *  thumbnail image.
    */
  private def thumbnailLocation(root: Elem): Option[String] =
    physicalStructMap(root).headOption
      .flatMap {
        case (_, fileId) =>
          fileObjects(root).get(fileId)
      }
      .map(_.stripPrefix("objects/"))

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
  private def fileObjects(root: Elem): Map[String, String] =
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
  private def physicalStructMap(root: Elem): ListMap[String, String] =
    (root \ "structMap")
      .filterByAttribute("TYPE", "PHYSICAL")
      .descendentsWithTag("div")
      .toMapping(
        keyAttrib = "ID",
        valueNode = "fptr",
        valueAttrib = "FILEID"
      )

  implicit class NodeSeqOps(nodes: NodeSeq) {

    def filterByAttribute(attrib: String, value: String) =
      nodes.filter(_ \@ attrib == value)

    def childrenWithTag(tag: String) =
      nodes.flatMap(_ \ tag)

    def descendentsWithTag(tag: String) =
      nodes.flatMap(_ \\ tag)

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
      ListMap(mappings: _*)
    }
  }
}
