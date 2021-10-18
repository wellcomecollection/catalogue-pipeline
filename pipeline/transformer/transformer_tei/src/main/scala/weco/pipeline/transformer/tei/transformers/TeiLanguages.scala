package weco.pipeline.transformer.tei.transformers

import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.work.{Note, NoteType}
import weco.pipeline.transformer.result.Result
import weco.pipeline.transformer.tei.data.TeiLanguageData

import scala.xml.{Elem, NodeSeq}

object TeiLanguages {

  def apply(xml: Elem): Result[(List[Language], List[Note])] =
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
  def parseLanguages(value: NodeSeq): Either[Throwable, (List[Language],List[Note])] =
    (value \ "textLang")
      .foldRight(Right((Nil,Nil)): Either[Throwable, (List[Language],List[Note])]) { case (n, Right((languageList, languageNoteList))) =>
        val label = n.text

        val mainLangId = (n \@ "mainLang").toLowerCase
        val otherLangId = (n \@ "otherLangs").toLowerCase

        val langId = (mainLangId, otherLangId) match {
          case (id1, id2) if id2.isEmpty && id1.nonEmpty => Right(Some(id1))
          case (id1, id2) if id1.isEmpty && id2.nonEmpty => Right(Some(id2))
          case (id1, id2) if id2.isEmpty && id1.isEmpty =>
            Right(None)
          case _ =>
            Left(new RuntimeException(s"Multiple language IDs in $n"))
        }

        (langId, label) match {
          case (Right(Some(id)), label) if label.trim.nonEmpty =>
            TeiLanguageData(id, label).map(l => (languageList :+ l, languageNoteList))
          case (Right(Some(id)), _) =>
            Left(new Throwable(s"Missing label for language node with id=$id"))
          case (Right(None), label) if label.trim.nonEmpty =>
            Right((languageList, languageNoteList :+ Note(NoteType.LanguageNote, label)))
          case (Left(err), _) => Left(err)
        }

      case (_, Left(err)) => Left(err)
      }
}
