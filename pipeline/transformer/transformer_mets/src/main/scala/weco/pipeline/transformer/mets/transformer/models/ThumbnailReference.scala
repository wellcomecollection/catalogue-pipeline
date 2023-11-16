package weco.pipeline.transformer.mets.transformer.models

import scala.xml.{Elem, Node, NodeSeq}

/*
 * The thumbnail of a METS document is derived from the first of:
 * 1. The title page, if this is explicitly defined
 * 2. The first file that could be a thumbnail
 *    * That is, a file which is either an image or a pdf.
 * */
object ThumbnailReference extends XMLOps {

  def apply(root: Elem): Option[FileReference] = {
    implicit val r: Elem = root
    titlePageFileNode.orElse(firstThumbnailableFileNode).map {
      fileElement =>
        val href =
          fileElement \ "FLocat" \@ "{http://www.w3.org/1999/xlink}href"
        FileReference(
          id = fileElement \@ "ID",
          location = href,
          listedMimeType = Option(fileElement \@ "MIMETYPE").filter(_.nonEmpty)
        )
    }
  }
  private def firstThumbnailableFileNode(implicit root: Elem): Option[Node] =
    fileNodes.find {
      node =>
        val mime = node \@ "MIMETYPE"
        isValidForFileReference(node) && (mime == "application/pdf" || mime
          .startsWith("image"))
    }
  private def titlePageFileNode(implicit root: Elem): Option[Node] = {
    TitlePageId(root).flatMap(fileIdFromPhysicalId).flatMap(validFileNodeWithId)
  }
  private def fileIdFromPhysicalId(
    id: String
  )(implicit root: Elem): Option[String] =
    (
      ((root \ "structMap").filterByAttribute("TYPE", "PHYSICAL") \\ "div")
        .filterByAttribute("ID", id) \ "fptr"
    ).map(_ \@ "FILEID").find(_.nonEmpty)

  private def validFileNodeWithId(
    id: String
  )(implicit root: Elem): Option[Node] =
    fileNodeFromObjectId(id).find(isValidForFileReference)

  private def isValidForFileReference(node: Node): Boolean =
    (node \ "FLocat" \ "@{http://www.w3.org/1999/xlink}href").nonEmpty
  private def fileNodes(implicit root: Elem): NodeSeq =
    (root \ "fileSec" \ "fileGrp").filterByAttribute(
      "USE",
      "OBJECTS"
    ) \ "file"

  private def fileNodeFromObjectId(id: String)(implicit root: Elem): NodeSeq =
    fileNodes.filterByAttribute("ID", id)

}
