package uk.ac.wellcome.models.work.internal

/** Work contains the work itself. It is parameterised by its state, meaning
  * the same type of Work can be in a number of possible states depending on
  * where in the pipeline it is. This allows us to easily add new types of work
  * (such as if Collection is decided to be a separate type to StandardWork),
  * with the state of the work in the pipeline being an orthogonal concern.
  */
sealed trait Work[State <: WorkState] {
  val sourceIdentifier: SourceIdentifier
  val version: Int
  def maybeData: Option[WorkData[State, State#ImageId]]
}

object Work {
  case class Standard[State <: WorkState](
    sourceIdentifier: SourceIdentifier,
    version: Int,
    data: WorkData[State, State#ImageId],
    state: State,
  ) extends Work[State] {

    def maybeData = Some(data)
  }

  case class Redirected[State <: WorkState](
    sourceIdentifier: SourceIdentifier,
    version: Int,
    state: State,
  ) extends Work[State] {

    def maybeData = None
  }

  case class Invisible[State <: WorkState](
    sourceIdentifier: SourceIdentifier,
    version: Int,
    data: WorkData[State, State#ImageId],
    state: State,
  ) extends Work[State] {

    def maybeData = Some(data)
  }
}

/** WorkData contains data common to all types of works that can exist at any
  * stage of the pipeline.
  */
case class WorkData[State <: WorkState, ImageId <: IdState.WithSourceIdentifier](
  title: Option[String] = None,
  otherIdentifiers: List[SourceIdentifier] = Nil,
  mergeCandidates: List[MergeCandidate] = Nil,
  alternativeTitles: List[String] = Nil,
  workType: Option[WorkType] = None,
  description: Option[String] = None,
  physicalDescription: Option[String] = None,
  lettering: Option[String] = None,
  createdDate: Option[Period[State#DataId]] = None,
  subjects: List[Subject[State#DataId]] = Nil,
  genres: List[Genre[State#DataId]] = Nil,
  contributors: List[Contributor[State#DataId]] = Nil,
  thumbnail: Option[LocationDeprecated] = None,
  production: List[ProductionEvent[State#DataId]] = Nil,
  language: Option[Language] = None,
  edition: Option[String] = None,
  notes: List[Note] = Nil,
  duration: Option[Int] = None,
  items: List[Item[State#DataId]] = Nil,
  merged: Boolean = false,
  collectionPath: Option[CollectionPath] = None,
  images: List[UnmergedImage[ImageId, State]] = Nil
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
  * Each WorkState also has an associated IdentifierState which indicates whether
  * the corresponding WorkData is pre or post the minter.
  */
sealed trait WorkState {

  type DataId <: IdState

  type ImageId <: IdState.WithSourceIdentifier

  val sourceIdentifier: SourceIdentifier
}

object WorkState {

  // TODO: for now just 2 states, in the end the states will correspond to the
  // block comment above

  case class Unidentified(
    sourceIdentifier: SourceIdentifier,
    identifiedType: String = classOf[Identified].getSimpleName,
  ) extends WorkState {

    type DataId = IdState.Unminted

    type ImageId = IdState.Identifiable
  }

  case class Identified(
    sourceIdentifier: SourceIdentifier,
    canonicalId: String,
    // relations: Relations[Identified],
  ) extends WorkState {

    type DataId = IdState.Minted

    type ImageId = IdState.Identified
  }
}
