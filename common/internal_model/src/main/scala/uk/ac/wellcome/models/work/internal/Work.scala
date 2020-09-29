package uk.ac.wellcome.models.work.internal

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

  def identifiers: List[SourceIdentifier] =
    sourceIdentifier :: data.otherIdentifiers

  def withData(
    f: WorkData[State#WorkDataState] => WorkData[State#WorkDataState])
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
    val outData = transition.data(data, args)
    this match {
      case Work.Visible(version, _, _) =>
        Work.Visible(version, outData, outState)
      case Work.Invisible(version, _, _, invisibilityReasons) =>
        Work.Invisible(version, outData, outState, invisibilityReasons)
      case Work.Redirected(version, redirect, _) =>
        Work.Redirected(version, transition.redirect(redirect, args), outState)
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
}

object WorkState {

  case class Source(
    sourceIdentifier: SourceIdentifier
  ) extends WorkState {

    type WorkDataState = DataState.Unidentified
    type TransitionArgs = Unit
  }

  case class Merged(
    sourceIdentifier: SourceIdentifier,
    hasMultipleSources: Boolean
  ) extends WorkState {

    type WorkDataState = DataState.Unidentified
    type TransitionArgs = Boolean
  }

  case class Denormalised(
    sourceIdentifier: SourceIdentifier,
    hasMultipleSources: Boolean = false,
    relations: Relations[Denormalised] = Relations.unknown
  ) extends WorkState {

    type WorkDataState = DataState.Unidentified
    type TransitionArgs = Relations[Denormalised]
  }

  case class Identified(
    sourceIdentifier: SourceIdentifier,
    canonicalId: String,
    hasMultipleSources: Boolean = false,
    relations: Relations[Identified] = Relations.unknown
  ) extends WorkState {

    type WorkDataState = DataState.Identified
    type TransitionArgs = Unit
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

    def data(data: WorkData[InState#WorkDataState],
             args: OutState#TransitionArgs): WorkData[OutState#WorkDataState]

    def redirect(redirect: InState#WorkDataState#Id,
                 args: OutState#TransitionArgs): OutState#WorkDataState#Id
  }

  implicit val sourceToMerged = new Transition[Source, Merged] {
    def state(state: Source, hasMultipleSources: Boolean): Merged =
      Merged(state.sourceIdentifier, hasMultipleSources)

    def data(data: WorkData[DataState.Unidentified],
             hasMultipleSources: Boolean): WorkData[DataState.Unidentified] =
      data

    def redirect(redirect: IdState.Identifiable,
                 hasMultipleSources: Boolean): IdState.Identifiable =
      redirect
  }

  implicit val mergedToDenormalised = new Transition[Merged, Denormalised] {
    def state(state: Merged, relations: Relations[Denormalised]): Denormalised =
      Denormalised(state.sourceIdentifier, state.hasMultipleSources, relations)

    def data(
      data: WorkData[DataState.Unidentified],
      relations: Relations[Denormalised]): WorkData[DataState.Unidentified] =
      data

    def redirect(redirect: IdState.Identifiable,
                 relations: Relations[Denormalised]): IdState.Identifiable =
      redirect
  }
}
