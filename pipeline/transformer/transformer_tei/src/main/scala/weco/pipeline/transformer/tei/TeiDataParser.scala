package weco.pipeline.transformer.tei

import weco.catalogue.internal_model.identifiers.DataState.Unidentified
import weco.catalogue.internal_model.identifiers.{IdentifierType, SourceIdentifier}
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.work.{MergeCandidate, Work, WorkData}
import weco.pipeline.transformer.identifiers.SourceIdentifierValidation._

import java.time.Instant

object TeiDataParser {
  def parse(teiXml: TeiXml): Either[Throwable, TeiData] =
    for {
      summary <- teiXml.summary
      bNumber <- teiXml.bNumber
      title <- teiXml.title
    languages <- teiXml.languages
    } yield TeiData(teiXml.id, title, bNumber, summary, languages)
}

case class TeiData(id: String, title: String, bNumber: Option[String], description: Option[String], languages: List[Language]) {
  def toWork(time: Instant, version: Int): Work[Source] = {
    val maybeBnumber = bNumber
      .flatMap { id =>
        SourceIdentifier(IdentifierType.SierraSystemNumber, "Work", id).validatedWithWarning
      }
      .map { sourceIdentifier =>
        MergeCandidate(
          identifier = sourceIdentifier,
          reason = "Bnumber present in TEI file"
        )
      }

    val value =
      WorkData[Unidentified](
        title = Some(title),
        description = description,
        mergeCandidates = maybeBnumber.toList,
        languages = languages
      )
    Work.Visible[Source](
      version,
      value,
      state = Source(SourceIdentifier(IdentifierType.Tei, "Work", id), time),
      Nil
    )
  }
}
