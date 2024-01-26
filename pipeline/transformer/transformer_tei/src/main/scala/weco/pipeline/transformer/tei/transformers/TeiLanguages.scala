package weco.pipeline.transformer.tei.transformers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.work.{Note, NoteType}
import weco.pipeline.transformer.result.Result
import weco.pipeline.transformer.tei.data.TeiLanguageData

import scala.xml.{Elem, Node, NodeSeq}

object TeiLanguages extends Logging {

  def apply(xml: Elem): Result[(List[Language], List[Note])] =
    parseLanguages(xml \\ "msDesc" \ "msContents")

  /** The languages of the TEI are in `textLang` nodes under `msContents`.
    *
    * <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id={id}> <teiHeader> <fileDesc> <sourceDesc>
    * <msDesc xml:lang="en" xml:id="MS_Arabic_1"> <msContents> <textLang mainLang={id}
    * source="IANA">{label}</textLang>
    *
    * This function extracts all the nodes from a parsed XML and returns a list of (id, label)
    * pairs.
    */
  def parseLanguages(value: NodeSeq): Result[(List[Language], List[Note])] =
    (value \ "textLang")
      .foldRight(
        Right((Nil, Nil)): Either[Throwable, (List[Language], List[Note])]
      ) {
        case (n, Right((languageList, languageNoteList))) =>
          val label = n.text

          val langId = parseLanguageId(n)

          (langId, label) match {
            case (Right(Some(id)), label) if label.trim.nonEmpty =>
              appendLanguageOrNote(languageList, languageNoteList, id, label)
            case (Right(None), label) if label.trim.nonEmpty =>
              appendNote(languageList, languageNoteList, label)
            case (Right(_), _) =>
              warn(s"Missing label for language node $n")
              Right((languageList, languageNoteList))
            case (Left(err), _) => Left(err)
          }

        case (_, Left(err)) => Left(err)
      }

  private def parseLanguageId(n: Node) = {
    val mainLangId = (n \@ "mainLang").toLowerCase
    val otherLangId = (n \@ "otherLangs").toLowerCase
    (mainLangId, otherLangId) match {
      case (id1, id2) if id2.isEmpty && id1.nonEmpty => Right(Some(id1))
      case (id1, id2) if id1.isEmpty && id2.nonEmpty => Right(Some(id2))
      case (id1, id2) if id2.isEmpty && id1.isEmpty =>
        Right(None)
      case _ =>
        Left(new RuntimeException(s"Multiple language IDs in $n"))
    }
  }

  private def appendLanguageOrNote(
    languageList: List[Language],
    languageNoteList: List[Note],
    id: String,
    label: String
  ) =
    TeiLanguageData(id, label).fold(
      err => {
        warn("Could not parse language", err)
        appendNote(languageList, languageNoteList, label)
      },
      appendLanguage(languageList, languageNoteList, _)
    )

  private def appendLanguage(
    languageList: List[Language],
    languageNoteList: List[Note],
    language: Language
  ) =
    Right((language +: languageList, languageNoteList))

  private def appendNote(
    languageList: List[Language],
    languageNoteList: List[Note],
    label: String
  ) =
    Right((languageList, languageNoteFrom(label) +: languageNoteList))

  private def languageNoteFrom(label: String) =
    Note(NoteType.LanguageNote, label)
}
