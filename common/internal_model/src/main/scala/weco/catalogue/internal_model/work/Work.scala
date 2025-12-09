package weco.catalogue.internal_model.work

import weco.catalogue.internal_model.identifiers._
import weco.catalogue.internal_model.image.ImageData
import weco.catalogue.internal_model.languages.Language
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

  def transition[OutState <: WorkState](args: OutState#TransitionArgs = ())(
    implicit transition: WorkFsm.Transition[State, OutState]
  ): Work[OutState] = {
    val outState = transition.state(state, data, args)
    val outData = transition.data(data)
    this match {
      case Work.Visible(version, _, _, redirectSources) =>
        Work.Visible(
          version,
          outData,
          outState,
          redirectSources.map {
            transition.redirect
          }
        )
      case Work.Invisible(version, _, _, invisibilityReasons) =>
        Work.Invisible(version, outData, outState, invisibilityReasons)
      case Work.Deleted(version, _, deletedReason) =>
        Work.Deleted(version, outState, deletedReason)
      case Work.Redirected(version, redirectTarget, _) =>
        Work.Redirected(version, transition.redirect(redirectTarget), outState)
    }
  }
}

object Work {

  case class Visible[State <: WorkState](
    version: Int,
    data: WorkData[State#WorkDataState],
    state: State,
    redirectSources: Seq[State#WorkDataState#Id] = Nil
  ) extends Work[State]

  case class Redirected[State <: WorkState](
    version: Int,
    redirectTarget: State#WorkDataState#Id,
    state: State
  ) extends Work[State] {
    val data = WorkData[State#WorkDataState]()
  }

  case class Invisible[State <: WorkState](
    version: Int,
    data: WorkData[State#WorkDataState],
    state: State,
    invisibilityReasons: List[InvisibilityReason] = Nil
  ) extends Work[State]

  case class Deleted[State <: WorkState](
    version: Int,
    state: State,
    deletedReason: DeletedReason
  ) extends Work[State] {
    val data: WorkData[State#WorkDataState] = WorkData[State#WorkDataState]()
  }
}

/** WorkData contains data common to all types of works that can exist at any
  * stage of the pipeline.
  */
case class WorkData[State <: DataState](
  title: Option[String] = None,
  otherIdentifiers: List[SourceIdentifier] = Nil,
  alternativeTitles: List[String] = Nil,
  format: Option[Format] = None,
  description: Option[String] = None,
  physicalDescription: Option[String] = None,
  lettering: Option[String] = None,
  createdDate: Option[Period[State#MaybeId]] = None,
  subjects: List[Subject[State#MaybeId]] = Nil,
  genres: List[Genre[State#MaybeId]] = Nil,
  contributors: List[Contributor[State#MaybeId]] = Nil,
  thumbnail: Option[DigitalLocation] = None,
  production: List[ProductionEvent[State#MaybeId]] = Nil,
  languages: List[Language] = Nil,
  edition: Option[String] = None,
  notes: List[Note] = Nil,
  duration: Option[Int] = None,
  items: List[Item[State#MaybeId]] = Nil,
  holdings: List[Holdings] = Nil,
  collectionPath: Option[CollectionPath] = None,
  referenceNumber: Option[ReferenceNumber] = None,
  imageData: List[ImageData[State#Id]] = Nil,
  workType: WorkType = WorkType.Standard,
  // In this context, frequency and designation refer to serial publications,
  // e.g. how many issues a journal publishes each year
  currentFrequency: Option[String] = None,
  formerFrequency: List[String] = Nil,
  designation: List[String] = Nil
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
  type TransitionArgs

  val sourceIdentifier: SourceIdentifier
  val modifiedTime: Instant
  val relations: Relations
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
    internalWorkStubs: List[InternalWork.Source] = Nil,
    relations: Relations = Relations.none
  ) extends WorkState {

    type WorkDataState = DataState.Unidentified
    type TransitionArgs = Unit

    def id = sourceIdentifier.toString

    val modifiedTime: Instant = sourceModifiedTime
  }

  case class Identified(
    sourceIdentifier: SourceIdentifier,
    canonicalId: CanonicalId,
    sourceModifiedTime: Instant,
    mergeCandidates: List[MergeCandidate[IdState.Identified]] = Nil,
    internalWorkStubs: List[InternalWork.Identified] = Nil,
    relations: Relations = Relations.none
  ) extends WorkState {

    type WorkDataState = DataState.Identified
    type TransitionArgs = Unit

    def id = canonicalId.toString

    val modifiedTime: Instant = sourceModifiedTime
  }

  case class Merged(
    sourceIdentifier: SourceIdentifier,
    canonicalId: CanonicalId,
    mergedTime: Instant,
    sourceModifiedTime: Instant,
    availabilities: Set[Availability] = Set.empty,
    relations: Relations = Relations.none,
    mergeCandidates: List[MergeCandidate[IdState.Identified]] = Nil
  ) extends WorkState {

    type WorkDataState = DataState.Identified
    type TransitionArgs = Instant

    def id: String = canonicalId.toString

    // This is used to order updates in pipeline-storage.
    // See https://github.com/wellcomecollection/docs/tree/main/rfcs/038-matcher-versioning
    val modifiedTime: Instant = mergedTime
  }
}

/** The WorkFsm contains all possible transitions between work states.
  *
  * The `transition` method on `Work` allows invocation of these transitions by
  * providing the type parameter of the new state and it's respective arguments.
  */
object WorkFsm {

  import WorkState._

  sealed trait Transition[InState <: WorkState, OutState <: WorkState] {

    def state(
      state: InState,
      data: WorkData[InState#WorkDataState],
      args: OutState#TransitionArgs
    ): OutState

    def data(
      data: WorkData[InState#WorkDataState]
    ): WorkData[OutState#WorkDataState]

    def redirect(redirect: InState#WorkDataState#Id): OutState#WorkDataState#Id
  }

  implicit val identifiedToMerged = new Transition[Identified, Merged] {
    def state(
      state: Identified,
      data: WorkData[DataState.Identified],
      mergedTime: Instant
    ): Merged =
      Merged(
        sourceIdentifier = state.sourceIdentifier,
        canonicalId = state.canonicalId,
        mergedTime = mergedTime,
        sourceModifiedTime = state.sourceModifiedTime,
        availabilities = Availabilities.forWorkData(data),
        relations = state.relations,
        mergeCandidates = state.mergeCandidates
      )

    def data(data: WorkData[DataState.Identified]) = data

    def redirect(redirect: IdState.Identified): IdState.Identified = redirect
  }
}
