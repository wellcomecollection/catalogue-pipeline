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
        Work.Visible[State](version, f(data), state)
      case Work.Invisible(version, data, state, reasons) =>
        Work.Invisible[State](version, f(data), state, reasons)
      case Work.Redirected(version, redirect, state) =>
        Work.Redirected[State](version, redirect, state)
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
  merged: Boolean = false,
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

  val sourceIdentifier: SourceIdentifier
}

object WorkState {

  case class Source(
    sourceIdentifier: SourceIdentifier
  ) extends WorkState {

    type WorkDataState = DataState.Unidentified
  }

  case class Merged(
    sourceIdentifier: SourceIdentifier,
    isMerged: Boolean
  ) extends WorkState {

    type WorkDataState = DataState.Unidentified
  }

  case class Denormalised(
    sourceIdentifier: SourceIdentifier
  ) extends WorkState {

    type WorkDataState = DataState.Unidentified
  }

  case class Identified(
    sourceIdentifier: SourceIdentifier,
    canonicalId: String,
  ) extends WorkState {

    type WorkDataState = DataState.Identified
  }
}

object WorkFsm {

  import WorkState._

  implicit class SourceWorkFsm(work: Work[Source]) {

    def transitionToMerged(isMerged: Boolean): Work[Merged] = {
      val state = Merged(work.sourceIdentifier, isMerged)
      work match {
        case Work.Visible(version, data, _) =>
          Work.Visible[Merged](version, data, state)
        case Work.Invisible(version, data, _, invisibilityReasons) =>
          Work.Invisible[Merged](version, data, state, invisibilityReasons)
        case Work.Redirected(version, redirect, _) =>
          Work.Redirected[Merged](version, redirect, state)
      }
    }
  }
}
