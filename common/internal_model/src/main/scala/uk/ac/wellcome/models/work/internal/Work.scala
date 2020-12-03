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

  def transition[OutState <: WorkState](args: OutState#TransitionArgs = ())(
    implicit transition: WorkFsm.Transition[State, OutState])
    : Work[OutState] = {
    val outState = transition.state(state, data, args)
    val outData = transition.data(data)
    this match {
      case Work.Visible(version, _, _) =>
        Work.Visible(version, outData, outState)
      case Work.Invisible(version, _, _, invisibilityReasons) =>
        Work.Invisible(version, outData, outState, invisibilityReasons)
      case Work.Deleted(version, _, deletedReasons) =>
        Work.Deleted(version, outState, deletedReasons)
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

  case class Deleted[State <: WorkState](
    version: Int,
    state: State,
    deletedReason: Option[DeletedReason] = None,
  ) extends Work[State] {
    val data = WorkData[State#WorkDataState]()
  }
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
  languages: List[Language] = Nil,
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
  *      | (id minter)
  *      ▼
  *  Identified
  *      |
  *      | (relation embedder)
  *      ▼
  * Denormalised
  *      |
  *      | (ingestor)
  *      ▼
  *   Indexed
  */
sealed trait WorkState {

  type WorkDataState <: DataState
  type TransitionArgs

  val sourceIdentifier: SourceIdentifier
  val modifiedTime: Instant

  def id: String = sourceIdentifier.toString
}

object WorkState {

  case class Source(
    sourceIdentifier: SourceIdentifier,
    modifiedTime: Instant
  ) extends WorkState {

    type WorkDataState = DataState.Unidentified
    type TransitionArgs = Unit
  }

  case class Merged(
    sourceIdentifier: SourceIdentifier,
    modifiedTime: Instant,
  ) extends WorkState {

    type WorkDataState = DataState.Unidentified
    type TransitionArgs = Option[Instant]
  }

  case class Identified(
    sourceIdentifier: SourceIdentifier,
    canonicalId: String,
    modifiedTime: Instant,
  ) extends WorkState {

    type WorkDataState = DataState.Identified
    type TransitionArgs = Unit

    override def id = canonicalId
  }

  case class Denormalised(
    sourceIdentifier: SourceIdentifier,
    canonicalId: String,
    modifiedTime: Instant,
    relations: Relations[DataState.Identified] = Relations.none
  ) extends WorkState {

    type WorkDataState = DataState.Identified
    type TransitionArgs = Relations[DataState.Identified]
  }

  case class Indexed(
    sourceIdentifier: SourceIdentifier,
    canonicalId: String,
    modifiedTime: Instant,
    derivedData: DerivedData,
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

    def state(state: InState,
              data: WorkData[InState#WorkDataState],
              args: OutState#TransitionArgs): OutState

    def data(
      data: WorkData[InState#WorkDataState]): WorkData[OutState#WorkDataState]

    def redirect(redirect: InState#WorkDataState#Id): OutState#WorkDataState#Id
  }

  implicit val sourceToMerged = new Transition[Source, Merged] {
    def state(state: Source,
              data: WorkData[DataState.Unidentified],
              args: Option[Instant]): Merged =
      Merged(
        state.sourceIdentifier,
        args.getOrElse(state.modifiedTime)
      )

    def data(data: WorkData[DataState.Unidentified]) = data

    def redirect(redirect: IdState.Identifiable) = redirect
  }

  implicit val identifiedToDenormalised =
    new Transition[Identified, Denormalised] {
      def state(state: Identified,
                data: WorkData[DataState.Identified],
                relations: Relations[DataState.Identified]): Denormalised =
        Denormalised(
          sourceIdentifier = state.sourceIdentifier,
          canonicalId = state.canonicalId,
          modifiedTime = state.modifiedTime,
          relations = relations
        )

      def data(data: WorkData[DataState.Identified]) = data

      def redirect(redirect: IdState.Identified) = redirect
    }

  implicit val denormalisedToIndexed = new Transition[Denormalised, Indexed] {
    def state(state: Denormalised,
              data: WorkData[DataState.Identified],
              args: Unit = ()): Indexed =
      Indexed(
        sourceIdentifier = state.sourceIdentifier,
        canonicalId = state.canonicalId,
        modifiedTime = state.modifiedTime,
        derivedData = DerivedData(data),
        relations = state.relations
      )

    def data(data: WorkData[DataState.Identified]) = data

    def redirect(redirect: IdState.Identified) = redirect
  }
}
