package weco.catalogue.internal_model.work

import weco.catalogue.internal_model.identifiers._
import weco.catalogue.internal_model.image.ImageData

import java.time.Instant

sealed trait Work[State <: WorkState] {

  val state: State
  val data: WorkData[State#WorkDataState]

  def id: String = state.id
}

case class WorkData[State <: DataState](
  otherIdentifiers: List[SourceIdentifier] = Nil,
  format: Option[Format] = None,
  subjects: List[Subject[State#MaybeId]] = Nil,
  genres: List[Genre[State#MaybeId]] = Nil,
  contributors: List[Contributor[State#MaybeId]] = Nil,
  production: List[ProductionEvent[State#MaybeId]] = Nil,
  notes: List[Note] = Nil,
  items: List[Item[State#MaybeId]] = Nil,
  holdings: List[Holdings] = Nil,
  imageData: List[ImageData[State#Id]] = Nil,
  workType: WorkType = WorkType.Standard
)

sealed trait WorkState {

  type WorkDataState <: DataState

  val sourceIdentifier: SourceIdentifier
  val modifiedTime: Instant
  def id: String
}

object InternalWork {
  case class Source(
    sourceIdentifier: SourceIdentifier,
    workData: WorkData[DataState.Unidentified]
  )

  case class Identified(
    sourceIdentifier: SourceIdentifier,
    canonicalId: CanonicalId,
    workData: WorkData[DataState.Identified]
  )
}

object WorkState {

  case class Source(
    sourceIdentifier: SourceIdentifier,
    sourceModifiedTime: Instant,
    mergeCandidates: List[MergeCandidate[IdState.Identifiable]] = Nil,
    internalWorkStubs: List[InternalWork.Source] = Nil
  ) extends WorkState {

    type WorkDataState = DataState.Unidentified

    def id = sourceIdentifier.toString

    val modifiedTime: Instant = sourceModifiedTime
  }

  case class Identified(
    sourceIdentifier: SourceIdentifier,
    canonicalId: CanonicalId,
    sourceModifiedTime: Instant,
    mergeCandidates: List[MergeCandidate[IdState.Identified]] = Nil,
    internalWorkStubs: List[InternalWork.Identified] = Nil
  ) extends WorkState {

    type WorkDataState = DataState.Identified

    def id = canonicalId.toString

    val modifiedTime: Instant = sourceModifiedTime
  }
}
