package weco.pipeline.transformer.tei.transformers


import weco.pipeline.transformer.tei.NormaliseText

import scala.xml.{Elem, Node, NodeSeq}

object TeiPhysicalDescription {
  def apply(xml: Elem): Option[String] =
    apply(xml \\"sourceDesc"\"msDesc")
  def apply(nodeSeq: NodeSeq): Option[String] = physicalDescription(nodeSeq)


  /**
   * The physical description can exist in msDesc for the wrapper work like in:
   *<TEI xml:id="id" xmlns="http://www.tei-c.org/ns/1.0">
   *   <teiHeader>
   *     <fileDesc>
   *       <sourceDesc>
   *         <msDesc xml:lang="en" xml:id="MS_Arabic_1">
   *           <physDesc>
   *             <objectDesc>
   *               <supportDesc>
   *                 <extent>3 pages
   *                   <dimensions unit="mm" type="leaf">
   *                     <height>100mm</height>
   *                     <width>300mm</width>
   *                   </dimensions>
   *                 </extent>
   *               </supportDesc>
   *
   * or in msPart for an internal work as in:
   * <msPart xml:id="">
   *  <physDesc>
   *    <objectDesc>
   *      <supportDesc>
   *        <support>Multiple manuscript parts collected in one volume.</support>
   *      </supportDesc>
   *    </objectDesc>
   */
  private def physicalDescription(nodeSeq: NodeSeq) = (nodeSeq \ "physDesc"\\"supportDesc").map{ supportDesc =>
    val materialString = (supportDesc \@ "material").trim
    val material = if(materialString.nonEmpty)s"Material: $materialString"else ""
    val support = parseSupport(supportDesc).toList
    val extent = parseExtent(supportDesc).toList
    (support ++ List(material) ++ extent).filterNot(_.isEmpty).mkString("; ")

  }.headOption


  /**
   * The extent contains information about the page count and dimension of the manuscript:
   * <extent>3 pages
   *  <dimensions unit="mm" type="leaf">
   *    <height>100mm</height>
   *    <width>300mm</width>
   *  </dimensions>
   * </extent>
   */
  private def parseExtent(supportDesc: Node) = {
    val extent = supportDesc \ "extent"
    val extentStr = if (extent.exists(_.child.size > 1)) {
      val extentLabel = extent.flatMap(_.child)
        .collect { case node if node.label != "dimensions" => node.text.trim }.mkString(" ").trim
      val dimensions = parseDimensions(extent)
      (extentLabel +: dimensions).filterNot(_.isEmpty).mkString("; ")
    }
    else extent.text.trim
    NormaliseText(extentStr)
  }

  /**
   * The support contain information about the material, the description and the watermarks present:
   * <support>Paper, folded in 2.
   *   <watermark>First watermark very similar to <ref>Mosin and Traljic 6947, 6949, 6956</ref> (saucisson), attested in 1338-50.</watermark>
   *   <watermark>Second watermark identical with <ref>Mosin and Traljic 5791</ref> (licorne), attested in 1339-44.</watermark>
   *   <measure type="chainline">Chain distance 43 mm.</measure>
   *   Added leaves (folios 1 and 198): paper folded in 2.
   *   <watermark>Watermarks similar to <ref>Piccard 122415</ref> (scissors), attested in 1457.</watermark>
   *   <measure type="chainline">Chain distance 35mm.</measure>
   * </support>
   * We extract the watermarks but we filter out the measure for now
   */
  private def parseSupport(supportDesc: Node) = {
    val support = (supportDesc \ "support")
    val supportStr = if (support.exists(_.child.size > 1)) {
      val watermarkStr = (support \ "watermark").text.trim
      val supportLabel = support.flatMap(_.child)
        .collect { case node if node.label != "watermark" && node.label != "measure" => node.text.trim }.mkString(" ").trim
      val parts = List(supportLabel, if (watermarkStr.nonEmpty) s"Watermarks: $watermarkStr" else "")
      parts.filterNot(_.isEmpty).mkString("; ")
    } else support.text.trim
    NormaliseText(supportStr)
  }

  /**
   * The dimension block contain information about the unit of measure, width and height.
   *  <dimensions unit="mm" type="leaf">
   *    <height>100</height>
   *    <width>300</width>
   *  </dimensions>
   */
  private def parseDimensions(extent: NodeSeq) =
    (extent \ "dimensions").map { dimensions =>
      val height = (dimensions \ "height").text.trim
      val width = (dimensions \ "width").text.trim
      val unit = (dimensions \@ "unit").trim
      val `type` = (dimensions \@ "type").trim
      val unitStr = if (unit.nonEmpty) unit else ""
      val heightStr = if (height.nonEmpty) s"height $height".trim else ""
      val widthStr = if (width.nonEmpty) s"width $width".trim else ""
      val dimensionStr = List(appendUnit(widthStr, unitStr), appendUnit(heightStr, unitStr)).mkString(", ")
      s"${`type`} dimensions: $dimensionStr"
    }

  // Sometimes the unit of measure is already appended in the width or height, so we check before appending
  private def appendUnit(str: String, unit: String) = if(str.trim.endsWith(unit)) str else s"$str $unit".trim
}
