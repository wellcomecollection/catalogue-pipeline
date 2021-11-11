package weco.pipeline.transformer.tei.transformers


import scala.xml.{Elem, Node, NodeSeq}

object TeiPhysicalDescription {
  def apply(xml: Elem): Option[String] =
    apply(xml \\"sourceDesc"\"msDesc")
  def apply(nodeSeq: NodeSeq): Option[String] = physicalDescription(nodeSeq)


  private def physicalDescription(nodeSeq: NodeSeq) = (nodeSeq \ "physDesc"\\"supportDesc").map{ supportDesc =>
    val materialString = (supportDesc \@ "material").trim
    val material = if(materialString.nonEmpty)s"Material: $materialString"else ""
    val support = parseSupport(supportDesc)
    val extent = parseExtent(supportDesc)
    List(support,material,extent).filterNot(_.isEmpty).mkString("; ")

  }.headOption

  private def parseExtent(supportDesc: Node) = {
    val extent = supportDesc \ "extent"
    val extentStr = if (extent.exists(_.child.size > 1)) {
      val extentLabel = extent.flatMap(_.child)
        .collect { case node if node.label != "dimensions" => node.text.trim }.mkString(" ").trim
      val dimensions = parseDimensions(extent)
      (extentLabel +: dimensions).filterNot(_.isEmpty).mkString("; ")
    }
    else extent.text.trim
    extentStr
  }

  private def parseSupport(supportDesc: Node) = {
    val support = (supportDesc \ "support")
    val supportStr = if (support.exists(_.child.size > 1)) {
      val watermarkStr = (support \ "watermark").text.trim
      val supportLabel = support.flatMap(_.child)
        .collect { case node if node.label != "watermark" && node.label != "measure" => node.text.trim }.mkString(" ").trim
      List(supportLabel, if (watermarkStr.nonEmpty) s"Watermarks: $watermarkStr" else "").filterNot(_.isEmpty).mkString("; ")
    } else support.text.trim
    supportStr
  }

  private def parseDimensions(extent: NodeSeq) =
    (extent \ "dimensions").map { dimensions =>
      val height = (dimensions \ "height").text.trim
      val width = (dimensions \ "width").text.trim
      val unit = (dimensions \@ "unit").trim
      val `type` = (dimensions \@ "type").trim
      val unitStr = if (unit.nonEmpty) unit else ""
      val heightStr = if (height.nonEmpty) s"height $height $unitStr".trim else ""
      val widthStr = if (width.nonEmpty) s"width $width $unitStr".trim else ""
      s"${`type`} dimensions: ${List(widthStr, heightStr).mkString(", ")}"
    }
}
