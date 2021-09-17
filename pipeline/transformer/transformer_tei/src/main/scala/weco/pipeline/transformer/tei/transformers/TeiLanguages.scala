package weco.pipeline.transformer.tei.transformers

import weco.catalogue.internal_model.languages.Language
import weco.pipeline.transformer.result.Result
import weco.pipeline.transformer.tei.data.TeiLanguageData
import cats.syntax.traverse._
import cats.instances.either._

import scala.xml.{Elem, NodeSeq}

object TeiLanguages {

  def apply(xml: Elem): Result[List[Language]] =
    parseLanguages(xml \\ "msDesc" \ "msContents")

  /** The languages of the TEI are in `textLang` nodes under `msContents`.
    *
    * <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id={id}>
    *   <teiHeader>
    *     <fileDesc>
    *       <sourceDesc>
    *         <msDesc xml:lang="en" xml:id="MS_Arabic_1">
    *           <msContents>
    *             <textLang mainLang={id} source="IANA">{label}</textLang>
    *
    * This function extracts all the nodes from a parsed XML and returns
    * a list of (id, label) pairs.
    */
  def parseLanguages(value: NodeSeq): Either[Throwable, List[Language]] =
    (value \ "textLang")
      .map { n =>
        val label = n.text

        val mainLangId = (n \@ "mainLang").toLowerCase
        val otherLangId = (n \@ "otherLangs").toLowerCase

        val langId = (mainLangId, otherLangId) match {
          case (id1, id2) if id2.isEmpty && id1.nonEmpty => Right(id1)
          case (id1, id2) if id1.isEmpty && id2.nonEmpty => Right(id2)
          case (id1, id2) if id2.isEmpty && id1.isEmpty =>
            Left(new RuntimeException(s"Cannot find a language ID in $n"))
          case _ =>
            Left(new RuntimeException(s"Multiple language IDs in $n"))
        }

        (langId, label) match {
          case (Right(id), label) if label.trim.nonEmpty =>
            TeiLanguageData(id, label)
          case (Left(err), _) => Left(err)
        }
      }
      .toList
      .sequence
}
