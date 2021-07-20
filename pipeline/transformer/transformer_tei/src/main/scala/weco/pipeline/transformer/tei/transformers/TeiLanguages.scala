package weco.pipeline.transformer.tei.transformers

import cats.implicits._
import grizzled.slf4j.Logging
import weco.catalogue.internal_model.languages.Language
import weco.pipeline.transformer.result.Result
import weco.pipeline.transformer.tei.TeiXml

import scala.xml.Elem

object TeiLanguages extends Logging {

  def apply(teiXml: TeiXml): Result[List[Language]] =
    apply(teiXml.xml)

  /** The languages of the TEI are in `textLang` nodes under `msContents`.
    *
    * <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id={id}>
    *  <teiHeader>
    *    <fileDesc>
    *      <sourceDesc>
    *        <msDesc xml:lang="en" xml:id="MS_Arabic_1">
    *          <msContents>
    *            <textLang mainLang={id} source="IANA">{label}</textLang>
    *
    * This function extracts all the nodes from a parsed XML and returns
    * a list of (id, label) pairs.
    */
  def findNodes(xml: Elem): Seq[(String, String)] =
    (xml \\ "msDesc" \ "msContents" \ "textLang").flatMap { n =>
      val label = n.text

      val mainLangId = (n \@ "mainLang").toLowerCase
      val otherLangId = (n \@ "otherLangs").toLowerCase

      val langId = (mainLangId, otherLangId) match {
        case (id1, id2) if id2.isEmpty && id1.nonEmpty => Some(id1)
        case (id1, id2) if id1.isEmpty && id2.nonEmpty => Some(id2)
        case (id1, id2) if id2.isEmpty && id1.isEmpty =>
          warn(s"Cannot find a language ID in $n")
          None
        case _ =>
          warn(s"Multiple language IDs in $n")
          None
      }

      (langId, label) match {
        case (Some(id), label) if label.trim.nonEmpty => Some((id, label))
        case _ => None
      }
    }

  /** The languages of the TEI are in `textLang` nodes under `msContents`.
    *
    * <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id={id}>
    *  <teiHeader>
    *    <fileDesc>
    *      <sourceDesc>
    *        <msDesc xml:lang="en" xml:id="MS_Arabic_1">
    *          <msContents>
    *            <textLang mainLang={id} source="IANA">{label}</textLang>
    *
    */
  def apply(xml: Elem): Result[List[Language]] = {
    val nodes = (xml \\ "msDesc" \ "msContents" \ "textLang").toList

    val eitherLanguages = nodes.map { n =>
      val label = n.text
      val mainLangId = (n \@ "mainLang").toLowerCase
      val otherLangId = (n \@ "otherLangs").toLowerCase
      val langId = (mainLangId, otherLangId) match {
        case (id1, id2) if id2.isEmpty && id1.nonEmpty => Right(id1)
        case (id1, id2) if id1.isEmpty && id2.nonEmpty => Right(id2)
        case (id1, id2) if id2.isEmpty && id1.isEmpty =>
          Left(
            new RuntimeException(
              s"Cannot find a language id in ${n.toString()}"
            )
          )
        case _ =>
          Left(
            new RuntimeException(s"Multiple language ids in ${n.toString()}")
          )
      }
      langId.map(id => Language(id = id, label = label))
    }
    eitherLanguages.sequence
  }
}
