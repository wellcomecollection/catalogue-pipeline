package weco.pipeline.transformer.mets.transformer

import org.apache.commons.lang3.NotImplementedException
import weco.pipeline.transformer.mets.transformer.models.{FileReference, XMLOps}
import weco.pipeline.transformer.mets.transformers.{
  MetsAccessConditions,
  ModsAccessConditions,
  PremisAccessConditions
}

import scala.util.{Left, Try}
import scala.xml.{Elem, NodeSeq, XML}

trait MetsXml extends XMLOps {
  val root: Elem
  def objectsFileGroupUse: String
  def firstManifestationFilename: Either[Exception, String]
  def recordIdentifier: Either[Exception, String]
  def metsIdentifier: Either[Exception, String]
  def accessConditions: Either[Throwable, MetsAccessConditions]

  def createdDate: Option[String] = {
    (root \ "metsHdr").headOption.map(_ \@ "CREATEDATE").filter(_.nonEmpty)
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
    * Seq("FILE_0001_OBJECTS", "FILE_0002_OBJECTS")
    */
  def physicalFileIds: Seq[String] =
    ((root \ "structMap")
      .filter(node => "physical".equalsIgnoreCase(node \@ "TYPE"))
      .descendentsWithTag("div")
      .sortByAttribute("ORDER") \ "fptr").map(_ \@ "FILEID")
}
case class ArchivematicaMetsXML(root: Elem) extends MetsXml {
  val objectsFileGroupUse: String = "original"
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

  def metsIdentifier: Either[Exception, String] = recordIdentifier
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
case class GoobiMetsXml(root: Elem) extends MetsXml {
  val objectsFileGroupUse: String = "OBJECTS"

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

  lazy val metsIdentifier: Either[Exception, String] = recordIdentifier

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

  lazy val accessConditions: Either[Throwable, MetsAccessConditions] =
    ModsAccessConditions(root).parse
}

object MetsXml {
  def apply(root: Elem): MetsXml = {
    if (isGoobi(root)) {
      GoobiMetsXml(root)
    } else if (isArchivematica(root)) {
      ArchivematicaMetsXML(root)
    } else {
      throw new NotImplementedError(
        "Could not determine which flavour of METS to parse"
      )
    }
  }
  private def isGoobi(root: Elem): Boolean =
    (root \ "metsHdr" \ "agent" \ "name").text.contains("Goobi")

  private def isArchivematica(root: Elem): Boolean =
    (root \ "amdSec" \ "digiprovMD" \ "mdWrap" \ "xmlData" \ "agent" \ "agentName").text
      .contains("Archivematica")

  def apply(str: String): Either[Throwable, MetsXml] =
    Try(XML.loadString(str)).map(MetsXml(_)).toEither
}
