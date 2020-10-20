package uk.ac.wellcome.models.work.internal

import java.time.Instant

/** Work is the core model in the pipeline / API.
  *
  * It is parameterised by State, meaning the same type of Work can be in a
  * number of possible states depending on where in the pipeline it is.
  */
sealed trait Work[State <: WorkState] {

  val version: Int
  val state: State
  val data: WorkData[State#WorkDataState]

  def sourceIdentifier: SourceIdentifier = state.sourceIdentifier

  def id: String = state.id

  def identifiers: List[SourceIdentifier] =
    sourceIdentifier :: data.otherIdentifiers

  def mapData(f: WorkData[State#WorkDataState] => WorkData[State#WorkDataState])
    : Work[State] =
    this match {
      case Work.Visible(version, data, state) =>
        Work.Visible(version, f(data), state)
      case Work.Invisible(version, data, state, reasons) =>
        Work.Invisible(version, f(data), state, reasons)
      case Work.Redirected(version, redirect, state) =>
        Work.Redirected(version, redirect, state)
    }

  def transition[OutState <: WorkState](args: OutState#TransitionArgs)(
    implicit transition: WorkFsm.Transition[State, OutState])
    : Work[OutState] = {
    val outState = transition.state(state, args)
    val outData = transition.data(data)
    this match {
      case Work.Visible(version, _, _) =>
        Work.Visible(version, outData, outState)
      case Work.Invisible(version, _, _, invisibilityReasons) =>
        Work.Invisible(version, outData, outState, invisibilityReasons)
      case Work.Redirected(version, redirect, _) =>
        Work.Redirected(version, transition.redirect(redirect), outState)
    }
  }
}

object Work {

  case class Visible[State <: WorkState](
    version: Int,
    data: WorkData[State#WorkDataState],
    state: State,
  ) extends Work[State]

  case class Redirected[State <: WorkState](
    version: Int,
    redirect: State#WorkDataState#Id,
    state: State,
  ) extends Work[State] {
    val data = WorkData[State#WorkDataState]()
  }

  case class Invisible[State <: WorkState](
    version: Int,
    data: WorkData[State#WorkDataState],
    state: State,
    invisibilityReasons: List[InvisibilityReason] = Nil,
  ) extends Work[State]
}

/** WorkData contains data common to all types of works that can exist at any
  * stage of the pipeline.
  */
case class WorkData[State <: DataState](
  title: Option[String] = None,
  otherIdentifiers: List[SourceIdentifier] = Nil,
  mergeCandidates: List[MergeCandidate] = Nil,
  alternativeTitles: List[String] = Nil,
  format: Option[Format] = None,
  description: Option[String] = None,
  physicalDescription: Option[String] = None,
  lettering: Option[String] = None,
  createdDate: Option[Period[State#MaybeId]] = None,
  subjects: List[Subject[State#MaybeId]] = Nil,
  genres: List[Genre[State#MaybeId]] = Nil,
  contributors: List[Contributor[State#MaybeId]] = Nil,
  thumbnail: Option[LocationDeprecated] = None,
  production: List[ProductionEvent[State#MaybeId]] = Nil,
  language: Option[Language] = None,
  edition: Option[String] = None,
  notes: List[Note] = Nil,
  duration: Option[Int] = None,
  items: List[Item[State#MaybeId]] = Nil,
  collectionPath: Option[CollectionPath] = None,
  images: List[UnmergedImage[State]] = Nil,
  workType: WorkType = WorkType.Standard,
)

/** WorkState represents the state of the work in the pipeline, and contains
  * different data depending on what state it is. This allows us to consider the
  * Work model as a finite state machine with the following stages corresponding
  * to stages of the pipeline:
  *
  *      |
  *      | (transformer)
  *      ▼
  *    Source
  *      |
  *      | (matcher / merger)
  *      ▼
  *   Merged
  *      |
  *      | (relation embedder)
  *      ▼
  * Denormalised
  *      |
  *      | (id minter)
  *      ▼
  *  Identified
  */
sealed trait WorkState {

  type WorkDataState <: DataState
  type TransitionArgs

  val sourceIdentifier: SourceIdentifier
  val numberOfSources: Int

  def id: String = sourceIdentifier.toString
}

object WorkState {

  case class Source(
    sourceIdentifier: SourceIdentifier,
    modifiedTime: Instant
  ) extends WorkState {

    type WorkDataState = DataState.Unidentified
    type TransitionArgs = Unit

    val numberOfSources = 1
  }

  case class Merged(
    sourceIdentifier: SourceIdentifier,
    numberOfSources: Int
  ) extends WorkState {

    type WorkDataState = DataState.Unidentified
    type TransitionArgs = Int
  }

  case class Denormalised(
    sourceIdentifier: SourceIdentifier,
    numberOfSources: Int = 1,
    relations: Relations[DataState.Unidentified] = Relations.none
  ) extends WorkState {

    type WorkDataState = DataState.Unidentified
    type TransitionArgs = Relations[DataState.Unidentified]
  }

  case class Identified(
    sourceIdentifier: SourceIdentifier,
    canonicalId: String,
    numberOfSources: Int = 1,
    relations: Relations[DataState.Identified] = Relations.none
  ) extends WorkState {

    type WorkDataState = DataState.Identified
    type TransitionArgs = Unit

    override def id = canonicalId
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

    def state(state: InState, args: OutState#TransitionArgs): OutState

    def data(
      data: WorkData[InState#WorkDataState]): WorkData[OutState#WorkDataState]

    def redirect(redirect: InState#WorkDataState#Id): OutState#WorkDataState#Id
  }

  implicit val sourceToMerged = new Transition[Source, Merged] {
    def state(state: Source, numberOfSources: Int): Merged =
      Merged(state.sourceIdentifier, numberOfSources)

    def data(data: WorkData[DataState.Unidentified]) = data

    def redirect(redirect: IdState.Identifiable) = redirect
  }

  implicit val mergedToDenormalised = new Transition[Merged, Denormalised] {
    def state(state: Merged,
              relations: Relations[DataState.Unidentified]): Denormalised =
      Denormalised(state.sourceIdentifier, state.numberOfSources, relations)

    def data(data: WorkData[DataState.Unidentified]) = data

    def redirect(redirect: IdState.Identifiable) = redirect
  }
}
