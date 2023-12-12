package weco.pipeline.transformer.mets.transformer

import org.apache.commons.lang3.NotImplementedException
import weco.pipeline.transformer.mets.transformer.models.{
  FileReference,
  ThumbnailReference,
  XMLOps
}
import weco.pipeline.transformer.mets.transformers.{
  MetsAccessConditions,
  ModsAccessConditions,
  PremisAccessConditions
}

import scala.util.{Left, Try}
import scala.xml.{Elem, NodeSeq, XML}

trait MetsXml {
  val root: Elem
  val thumbnailReference: Option[FileReference]
  def firstManifestationFilename: Either[Exception, String]
  def recordIdentifier: Either[Exception, String]

  def accessConditions: Either[Throwable, MetsAccessConditions]
}
case class ArchivematicaMetsXML(root: Elem) extends MetsXml with XMLOps {
  lazy val thumbnailReference: Option[FileReference] = ???

  // I don't yet have any examples of records with separate manifestations
  def firstManifestationFilename: Either[Exception, String] = Left(
    new NotImplementedException
  )

  def fileReferences: List[FileReference] = Nil

  def recordIdentifier: Either[Exception, String] =
    root \ "dmdSec" \ "mdWrap" \ "xmlData" \ "dublincore" \ "identifier" match {
      case NodeSeq.Empty =>
        Left(new RuntimeException("could not find record identifier"))
      case nodeseq if nodeseq.length == 1 => Right(nodeseq.head.text)
      case _ =>
        Left(
          new RuntimeException("multiple candidate record identifiers found")
        )
    }

  def accessConditions: Either[Throwable, MetsAccessConditions] =
    (root \ "amdSec" \ "rightsMD").headOption
      .map(PremisAccessConditions(_)) match {
      case Some(conditions) => conditions.parse
      case None =>
        Left(
          new RuntimeException(
            // I don't yet know if this is strictly true, or whether we can define
            // a default value for any missing ones.
            "Archivematica Mets file must contain a premis-compatible rightsMD element"
          )
        )
    }
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
