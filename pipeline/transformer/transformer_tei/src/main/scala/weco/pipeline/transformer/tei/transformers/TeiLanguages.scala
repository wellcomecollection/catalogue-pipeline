package weco.pipeline.transformer.tei.transformers

import weco.catalogue.internal_model.languages.Language
import weco.pipeline.transformer.result.Result
import weco.pipeline.transformer.tei.TeiXml
import weco.pipeline.transformer.tei.data.TeiLanguageData

import scala.util.{Failure, Success, Try}
import scala.xml.Elem

object TeiLanguages {

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
  def findNodes(xml: Elem): Try[Seq[(String, String)]] =
    Try {
      (xml \\ "msDesc" \ "msContents" \ "textLang").flatMap { n =>
        val label = n.text

        val mainLangId = (n \@ "mainLang").toLowerCase
        val otherLangId = (n \@ "otherLangs").toLowerCase

        val langId = (mainLangId, otherLangId) match {
          case (id1, id2) if id2.isEmpty && id1.nonEmpty => Some(id1)
          case (id1, id2) if id1.isEmpty && id2.nonEmpty => Some(id2)
          case (id1, id2) if id2.isEmpty && id1.isEmpty =>
            throw new RuntimeException(s"Cannot find a language ID in $n")
          case _ =>
            throw new RuntimeException(s"Multiple language IDs in $n")
        }

        (langId, label) match {
          case (Some(id), label) if label.trim.nonEmpty => Some((id, label))
          case _ => None
        }
      }
    }

  def apply(teiXml: TeiXml): Result[List[Language]] =
    apply(teiXml.xml)

  def apply(xml: Elem): Result[List[Language]] =
    findNodes(xml) match {
      case Success(languages) =>
        Right(
          languages
            .flatMap { case (id, label) =>
              TeiLanguageData(id = id, label = label)
            }
            .toList
        )

      case Failure(err) => Left(err)
    }
}
