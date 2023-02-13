package weco.catalogue.internal_model.work

import weco.catalogue.internal_model.identifiers._
import weco.catalogue.internal_model.image.ImageData
import weco.catalogue.internal_model.locations.DigitalLocation

import java.time.Instant

/** Work is the core model in the pipeline / API.
  *
  * It is parameterised by State, meaning the same type of Work can be in a
  * number of possible states depending on where in the pipeline it is.
  */
sealed trait Work[State <: WorkState] {

  val state: State
  val data: WorkData[State#WorkDataState]

  def sourceIdentifier: SourceIdentifier = state.sourceIdentifier

  // This version comes from the version in the adapter, so we can trace
  // a Work back to the exact source record that was used to create it
  // in the transformer.
  //
  // It should only be trusted for ordering updates of an individual Work.
  // You cannot compare the version between different Works -- use the
  // modifiedTime instead.

  // NOTE: Beware of changing the name or position of this field. The transformer
  // removes the version from the json when comparing two works to determine if they're equivalent.
  // Renaming/moving this field will make the check fail silently and could cause unnecessary
  // work to be performed by the pipeline
  val version: Int

  def id: String = state.id

  def identifiers: List[SourceIdentifier] =
    sourceIdentifier :: data.otherIdentifiers
}

/** WorkData contains data common to all types of works that can exist at any
  * stage of the pipeline.
  */
case class WorkData[State <: DataState](
  otherIdentifiers: List[SourceIdentifier] = Nil,
  format: Option[Format] = None,
  createdDate: Option[Period[State#MaybeId]] = None,
  subjects: List[Subject[State#MaybeId]] = Nil,
  genres: List[Genre[State#MaybeId]] = Nil,
  contributors: List[Contributor[State#MaybeId]] = Nil,
  thumbnail: Option[DigitalLocation] = None,
  production: List[ProductionEvent[State#MaybeId]] = Nil,
  notes: List[Note] = Nil,
  items: List[Item[State#MaybeId]] = Nil,
  holdings: List[Holdings] = Nil,
  collectionPath: Option[CollectionPath] = None,
  referenceNumber: Option[ReferenceNumber] = None,
  imageData: List[ImageData[State#Id]] = Nil,
  workType: WorkType = WorkType.Standard
)

/** WorkState represents the state of the work in the pipeline, and contains
  * different data depending on what state it is. This allows us to consider the
  * Work model as a finite state machine with the following stages corresponding
  * to stages of the pipeline:
  *
  * \| \| (transformer) ▼ Source \| \| (id minter) ▼ Identified \| \| (matcher /
  * merger) ▼ Merged \| \| (relation embedder) ▼ Denormalised
  */
sealed trait WorkState {

  type WorkDataState <: DataState

  val sourceIdentifier: SourceIdentifier
  val modifiedTime: Instant
  def id: String
}

object InternalWork {
  // Originally we used a full instance of Work[Source] and Work[Identified] here,
  // but for reasons we don't fully understand, that causes the compilation times of
  // internal_model to explode.
  //
  // This is probably a sign that the entire Id/Data/WorkState hierarchy needs a rethink
  // to make it less thorny and complicated, but doing that now would block the TEI work.
  //
  // TODO: Investigate the internal model compilation slowness further.
  // See https://github.com/wellcomecollection/platform/issues/5298
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
    // NOTE: Beware of changing the name or position of this field. The transformer
    // removes the sourceModifiedTime from the json when comparing two works to determine if they're equivalent.
    // Renaming/moving this field will make the check fail silently and could cause unnecessary
    // work to be performed by the pipeline
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
