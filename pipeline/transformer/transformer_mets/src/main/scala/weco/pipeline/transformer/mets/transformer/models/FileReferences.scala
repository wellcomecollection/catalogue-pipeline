package weco.pipeline.transformer.mets.transformer.models

import weco.pipeline.transformer.mets.transformer.MetsXml

object FileReferences extends XMLOps {
  def apply(metsXml: MetsXml): List[FileReference] = {
    implicit val x: MetsXml = metsXml
    physicalFileIds.flatMap(fileId => getFileReferences(fileId)).toList
  }

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
  private def getFileReferences(id: String)(
    implicit metsXml: MetsXml
  ): Seq[FileReference] = for {
    fileGrp <- metsXml.root \ "fileSec" \ "fileGrp"
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
  private def physicalFileIds(implicit metsXml: MetsXml): Seq[String] =
    ((metsXml.root \ "structMap")
      .filter(node => "physical".equalsIgnoreCase(node \@ "TYPE"))
      .descendentsWithTag("div")
      .sortByAttribute("ORDER") \ "fptr").map(_ \@ "FILEID")
}
