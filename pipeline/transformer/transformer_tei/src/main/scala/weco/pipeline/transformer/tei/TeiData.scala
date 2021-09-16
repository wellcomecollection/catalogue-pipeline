package weco.pipeline.transformer.tei

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers.DataState.Unidentified
import weco.catalogue.internal_model.identifiers.IdState.Identifiable
import weco.catalogue.internal_model.identifiers.{IdentifierType, SourceIdentifier}
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.work.{Format, InternalWork, MergeCandidate, Work, WorkData}
import weco.pipeline.transformer.identifiers.SourceIdentifierValidation._
import weco.pipeline.transformer.result.Result

import java.time.Instant

case class TeiData(id: String,
                   title: String,
                   bNumber: Option[String] = None,
                   description: Option[String] = None,
                   languages: List[Language] = Nil,
                   nestedTeiData: Result[List[TeiData]] = Right(Nil)) extends Logging{
  def toWork(time: Instant, version: Int): Work[Source] = {
    val topLevelData = toWorkData(mergeCandidates = mergeCandidates)

    val internalDataStubs =
      nestedTeiData.map { teiDatas =>
        teiDatas.map { data =>
          InternalWork.Source(
            data.sourceIdentifier,
            data.toWorkData(mergeCandidates = Nil)
          )
        }
      }

    // If there's only a single inner data, we move it to the top level
    // and don't send any inner Works.
    //
    // TODO: check logic for copying languages from wrapping works to inner works
    val (workData, internalWorkStubs) = internalDataStubs match {
      case Right(List(InternalWork.Source(_, singleItemData))) =>
        (topLevelData.copy(title = singleItemData.title), List())
      case Right(data) => (topLevelData, data)
      case Left(err) =>
        warn("Error extracting internal works", err)
        (topLevelData, List())
    }

    Work.Visible[Source](
      version = version,
      data = workData,
      state =
        Source(sourceIdentifier, time, internalWorkStubs = internalWorkStubs),
      redirectSources = Nil
    )
  }

  private def sourceIdentifier =
    SourceIdentifier(
      identifierType = IdentifierType.Tei,
      ontologyType = "Work",
      value = id
    )

  private def mergeCandidates: List[MergeCandidate[Identifiable]] = {
    val bNumberMergeCandidate = for {
      id <- bNumber
      sourceIdentifier <- SourceIdentifier(
        identifierType = IdentifierType.SierraSystemNumber,
        ontologyType = "Work",
        value = id).validatedWithWarning
    } yield
      MergeCandidate(
        identifier = sourceIdentifier,
        reason = "Bnumber present in TEI file"
      )

    bNumberMergeCandidate.toList
  }

  private def toWorkData(mergeCandidates: List[MergeCandidate[Identifiable]])
    : WorkData[Unidentified] =
    WorkData[Unidentified](
      title = Some(title),
      description = description,
      mergeCandidates = mergeCandidates,
      languages = languages,
      format = Some(Format.ArchivesAndManuscripts)
    )
}
