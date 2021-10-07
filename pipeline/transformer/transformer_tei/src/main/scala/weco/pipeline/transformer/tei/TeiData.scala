package weco.pipeline.transformer.tei

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers.DataState.Unidentified
import weco.catalogue.internal_model.identifiers.IdState.Identifiable
import weco.catalogue.internal_model.identifiers.{IdentifierType, SourceIdentifier}
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.work.WorkState.Source
import weco.catalogue.internal_model.work.{CollectionPath, Format, InternalWork, MergeCandidate, Work, WorkData}
import weco.pipeline.transformer.identifiers.SourceIdentifierValidation._

import java.time.Instant

case class TeiData(id: String,
                   title: String,
                   bNumber: Option[String] = None,
                   description: Option[String] = None,
                   languages: List[Language] = Nil,
                   nestedTeiData: List[TeiData] =Nil)
    extends Logging {
  def toWork(time: Instant, version: Int): Work[Source] = {
    val topLevelData = toWorkData()

    def iterateNestedData(nestedTeiData: List[TeiData], topLevelData: WorkData[Unidentified]): List[InternalWork.Source] =
      nestedTeiData.foldLeft(Nil: List[InternalWork.Source]) {case (l,data) =>
        val upperLevelWorkData = data.toWorkData(topLevelData.collectionPath)
        (l :+ InternalWork.Source(
        sourceIdentifier = data.sourceIdentifier,
        workData = upperLevelWorkData
      )) ++ iterateNestedData(data.nestedTeiData, upperLevelWorkData)
    }

    val internalWorks: List[InternalWork.Source] = iterateNestedData(nestedTeiData, topLevelData)

    val state = Source(
      sourceIdentifier = sourceIdentifier,
      sourceModifiedTime = time,
      internalWorkStubs = internalWorks.withLanguage(topLevelData),
      mergeCandidates = mergeCandidates
    )

    Work.Visible[Source](
      version = version,
      data = topLevelData,
      state = state,
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
  implicit class InternalWorkOps(internalWorks: List[InternalWork.Source]) {
    def withLanguage(
      topLevel: WorkData[Unidentified]): List[InternalWork.Source] =
      // If all the individual items/parts all use the same language,
      // it's specified once at the top level but not on the individual
      // entries.  The individual entries will not have languages.
      //
      // In this case, we copy the languages from the top-level entry
      // onto the individual items/parts, so they'll appear on the
      // corresponding Works.
      internalWorks.flatMap(_.workData.languages) match {
        case Nil =>
          internalWorks.map { w =>
            w.copy(
              workData = w.workData.copy(languages = topLevel.languages)
            )
          }

        case _ => internalWorks
      }
  }
  private def toWorkData(parentCollectionPath: Option[CollectionPath] = None)
    : WorkData[Unidentified] =
    WorkData[Unidentified](
      title = Some(title),
      description = description,
      languages = languages,
      format = Some(Format.ArchivesAndManuscripts),
      //
      // If a TEI work has multiple parts, we want to arrange it into a hierarchy
      // like a CALM collection, e.g.
      //
      //    Collection of manuscripts bound together
      //      ├── Individual manuscript #1
      //      ├── Individual manuscript #2
      //      └── Individual manuscript #3
      //
      // We use the IDs to construct the collection hierarchy, but we don't want to display
      // them internally.
      collectionPath = parentCollectionPath match {
        case Some(CollectionPath(parentPath, _)) =>
          Some(CollectionPath(path = s"$parentPath/$id"))
        case None => Some(CollectionPath(path = id))
      }
    )
}
