package weco.pipeline.transformer.mets.transformer.models

import weco.pipeline.transformer.mets.transformer.MetsXml

object FileReferences extends XMLOps {
  def apply(metsXml: MetsXml): List[FileReference] = {
    metsXml.physicalFileIds
      .flatMap(fileId => getFileReferences(metsXml, fileId))
      .toList
  }

  /** The METS XML contains locations of associated files, contained in a mapping with the following
    * format:
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
  private def getFileReferences(
    metsXml: MetsXml,
    id: String
  ): Seq[FileReference] =
    for {
      fileGrp <- metsXml.root \ "fileSec" \ "fileGrp"
      objects <- fileGrp.find(_ \@ "USE" == metsXml.objectsFileGroupUse)
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

}
