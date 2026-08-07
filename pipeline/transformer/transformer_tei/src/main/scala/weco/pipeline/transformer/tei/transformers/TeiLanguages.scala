package weco.pipeline.transformer.tei.transformers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.work.{Note, NoteType}
import weco.pipeline.transformer.result.Result
import weco.pipeline.transformer.tei.data.TeiLanguageData

import java.util.Locale
import scala.xml.{Elem, Node, NodeSeq}

object TeiLanguages extends Logging {

  def apply(xml: Elem): Result[(List[Language], List[Note])] =
    parseLanguages(xml \\ "msDesc" \ "msContents")

  /** The languages of the TEI are in `textLang` nodes under `msContents`.
    *
    * <TEI xmlns="http://www.tei-c.org/ns/1.0" xml:id={id}> <teiHeader>
    * <fileDesc> <sourceDesc> <msDesc xml:lang="en" xml:id="MS_Arabic_1">
    * <msContents> <textLang mainLang={id} source="IANA">{label}</textLang>
    *
    * This function extracts all the nodes from a parsed XML and returns a list
    * of (id, label) pairs.
    */
  def parseLanguages(value: NodeSeq): Result[(List[Language], List[Note])] =
    Right(
      (value \ "textLang")
        .foldRight((List.empty[Language], List.empty[Note])) {
          case (n, (languageList, languageNoteList)) =>
            val label = n.text

            (parseLanguageIds(n), label) match {
              case (_, label) if label.trim.isEmpty =>
                warn(s"Missing label for language node $n")
                (languageList, languageNoteList)
              case (Nil, label) =>
                appendNote(languageList, languageNoteList, label)
              case (ids, label) =>
                appendLanguagesOrNote(
                  languageList,
                  languageNoteList,
                  ids,
                  label
                )
            }
        }
    )

  /** A textLang may name a main language, other languages, or both. Both is how
    * TEI describes a multi-language manuscript, so read every id rather than
    * treating the combination as an error. Lowercasing is pinned to Locale.ROOT
    * so an id cannot stop matching under the JVM's default locale.
    */
  private def parseLanguageIds(n: Node): List[String] =
    (Seq(n \@ "mainLang") ++ (n \@ "otherLangs").split("\\s+"))
      .map(_.toLowerCase(Locale.ROOT).trim)
      .filter(_.nonEmpty)
      .distinct
      .toList

  private def appendLanguagesOrNote(
    languageList: List[Language],
    languageNoteList: List[Note],
    ids: List[String],
    label: String
  ): (List[Language], List[Note]) = {
    val languages = ids.flatMap {
      id =>
        TeiLanguageData(id, label) match {
          case Right(language) => Some(language)
          case Left(err) =>
            warn("Could not parse language", err)
            None
        }
    }

    // The label is shared by every id on the node, so it only becomes a note
    // when it yielded no language at all.
    if (languages.isEmpty) appendNote(languageList, languageNoteList, label)
    else (languages ++ languageList, languageNoteList)
  }

  private def appendNote(
    languageList: List[Language],
    languageNoteList: List[Note],
    label: String
  ): (List[Language], List[Note]) =
    (languageList, languageNoteFrom(label) +: languageNoteList)

  private def languageNoteFrom(label: String) =
    Note(NoteType.LanguageNote, label)
}
