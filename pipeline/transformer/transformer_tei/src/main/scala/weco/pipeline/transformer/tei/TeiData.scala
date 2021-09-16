package weco.pipeline.transformer.tei

import weco.catalogue.internal_model.identifiers.DataState.Unidentified
import weco.catalogue.internal_model.identifiers.IdState.Identifiable
import weco.catalogue.internal_model.identifiers.{IdentifierType, SourceIdentifier}
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.work.{Format, InternalWork, MergeCandidate, Work, WorkData}
import weco.pipeline.transformer.identifiers.SourceIdentifierValidation._

import java.time.Instant

case class TeiData(id: String,
                   title: String,
                   bNumber: Option[String] = None,
                   description: Option[String] = None,
                   languages: List[Language] = Nil,
                   internalTeiData: List[TeiData] = Nil) {
  def toWork(time: Instant, version: Int): Work[Source] = {
    val maybeBnumber =
      for {
        id <-bNumber
        sourceIdentifier <-  SourceIdentifier(IdentifierType.SierraSystemNumber, "Work", id).validatedWithWarning
      }yield MergeCandidate(
              identifier = sourceIdentifier,
              reason = "Bnumber present in TEI file"
      )

    val internalWorkStubs =
      internalTeiData.map { data =>
        InternalWork.Source(
          data.sourceIdentifier, data.toWorkData(mergeCandidates = Nil)
        )
      }

    Work.Visible[Source](
      version = version,
      data = toWorkData(mergeCandidates = maybeBnumber.toList),
      state = Source(sourceIdentifier, time, internalWorkStubs = internalWorkStubs),
      redirectSources = Nil
    )
  }

  private def sourceIdentifier = SourceIdentifier(IdentifierType.Tei, "Work", id)

  private def toWorkData(mergeCandidates: List[MergeCandidate[Identifiable]]): WorkData[Unidentified] =
    WorkData[Unidentified](
      title = Some(title),
      description = description,
      mergeCandidates = mergeCandidates,
      languages = languages,
      format = Some(Format.ArchivesAndManuscripts)
    )
}
