package uk.ac.wellcome.models.work.internal

/** Work is the core model in the pipeline / API.
  *
  * It is parameterised by State, meaning the same type of Work can be in a
  * number of possible states depending on where in the pipeline it is.
  */
sealed trait Work[State <: WorkState] {

  val version: Int
  val state: State
  val data: WorkData[State#MintState]

  def sourceIdentifier: SourceIdentifier = state.sourceIdentifier

  def identifiers: List[SourceIdentifier] =
    sourceIdentifier :: data.otherIdentifiers

  def withData(
    f: WorkData[State#MintState] => WorkData[State#MintState])
    : Work[State] =
    this match {
      case Work.Standard(version, data, state) =>
        Work.Standard[State](version, f(data), state)
      case Work.Invisible(version, data, state, reasons) =>
        Work.Invisible[State](version, f(data), state, reasons)
      case Work.Redirected(version, redirect, state) =>
        Work.Redirected[State](version, redirect, state)
    }
}

object Work {

  case class Standard[State <: WorkState](
    version: Int,
    data: WorkData[State#MintState],
    state: State,
  ) extends Work[State]

  case class Redirected[State <: WorkState](
    version: Int,
    redirect: State#MintState#Id,
    state: State,
  ) extends Work[State] {

    val data = WorkData[State#MintState]()
  }

  case class Invisible[State <: WorkState](
    version: Int,
    data: WorkData[State#MintState],
    state: State,
    invisibilityReasons: List[InvisibilityReason] = Nil,
  ) extends Work[State]
}

/** WorkData contains data common to all types of works that can exist at any
  * stage of the pipeline.
  */
case class WorkData[State <: MinterState](
  title: Option[String] = None,
  otherIdentifiers: List[SourceIdentifier] = Nil,
  mergeCandidates: List[MergeCandidate] = Nil,
  alternativeTitles: List[String] = Nil,
  workType: Option[WorkType] = None,
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
  images: List[UnmergedImage[State]] = Nil
)

/** WorkState represents the state of the work in the pipeline, and contains
  * different data depending on what state it is. This allows us to consider the
  * Work model as a finite state machine with the following stages corresponding
  * to stages of the pipeline:
  *
  *      |
  *      | (transformer)
  *      ▼
  *   Unmerged
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
  *
  * Each WorkState also has two associated ID types: MaybeId is used for data
  * that potentially is identifiable / identified, and Id is used for data that
  * is either identifiable or identified. The value of these types are 
  * the corresponding WorkData is pre or post the minter.
  */
sealed trait WorkState {

  type MintState <: MinterState

  val sourceIdentifier: SourceIdentifier
}

object WorkState {

  // TODO: for now just 2 states, in the end the states will correspond to the
  // block comment above

  case class Unidentified(
    sourceIdentifier: SourceIdentifier
  ) extends WorkState {

    type MintState = MinterState.Unminted
  }

  case class Identified(
    sourceIdentifier: SourceIdentifier,
    canonicalId: String,
  ) extends WorkState {

    type MintState = MinterState.Minted
  }
}
