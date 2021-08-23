package weco.catalogue.internal_model.work

import weco.catalogue.internal_model.identifiers.{
  CanonicalId,
  DataState,
  IdState,
  ReferenceNumber,
  SourceIdentifier
}
import weco.catalogue.internal_model.image.ImageData
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.locations.Location

import java.time.Instant

/** Work is the core model in the pipeline / API.
  *
  * It is parameterised by State, meaning the same type of Work can be in a
  * number of possible states depending on where in the pipeline it is.
  */
sealed trait Work[State <: WorkState] {

  val version: Int
  val state: State
  val workData: Option[WorkData[State#WorkDataState]]

  def sourceIdentifier: SourceIdentifier = state.sourceIdentifier

  def id: String = state.id

  def identifiers: List[SourceIdentifier] =
    sourceIdentifier :: workData.map(_.otherIdentifiers).getOrElse(List())

  def transition[OutState <: WorkState](args: OutState#TransitionArgs = ())(
    implicit transition: WorkFsm.Transition[State, OutState])
    : Work[OutState] = {
    val outState = transition.state(state, workData, args)
    val outData = transition.data(workData)
    this match {
      case Work.Visible(version, workData, _, redirectSources) =>
        val sources = redirectSources.map { transition.redirect }
        Work.Visible(version, transition.data(workData), outState, sources)
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
  ) extends Work[State] {
    override val workData: Option[WorkData[State#WorkDataState]] =
      Some(data)
  }

  case class Redirected[State <: WorkState](
    version: Int,
    redirectTarget: State#WorkDataState#Id,
    state: State,
  ) extends Work[State] {
    val workData: Option[WorkData[State#WorkDataState]] = None
  }

  case class Invisible[State <: WorkState](
    version: Int,
    data: Option[WorkData[State#WorkDataState]] = None,
    state: State,
    invisibilityReasons: List[InvisibilityReason],
  ) extends Work[State] {
    val workData: Option[WorkData[State#WorkDataState]] = data
  }

  case class Deleted[State <: WorkState](
    version: Int,
    state: State,
    deletedReason: DeletedReason,
  ) extends Work[State] {
    val workData: Option[WorkData[State#WorkDataState]] = None
  }
}

/** WorkData contains data common to all types of works that can exist at any
  * stage of the pipeline.
  */
case class WorkData[State <: DataState](
  title: Option[String] = None,
  otherIdentifiers: List[SourceIdentifier] = Nil,
  mergeCandidates: List[MergeCandidate[State#Id]] = Nil,
  alternativeTitles: List[String] = Nil,
  format: Option[Format] = None,
  description: Option[String] = None,
  physicalDescription: Option[String] = None,
  lettering: Option[String] = None,
  createdDate: Option[Period[State#MaybeId]] = None,
  subjects: List[Subject[State#MaybeId]] = Nil,
  genres: List[Genre[State#MaybeId]] = Nil,
  contributors: List[Contributor[State#MaybeId]] = Nil,
  thumbnail: Option[Location] = None,
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
  *      | (id minter)
  *      ▼
  *   Identified
  *      |
  *      | (matcher / merger)
  *      ▼
  *  Merged
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
  val relations: Relations
  def id: String
}

object WorkState {

  case class Source(
    sourceIdentifier: SourceIdentifier,
    sourceModifiedTime: Instant
  ) extends WorkState {

    type WorkDataState = DataState.Unidentified
    type TransitionArgs = Unit

    def id = sourceIdentifier.toString
    val relations = Relations.none

    override val modifiedTime: Instant = sourceModifiedTime
  }

  case class Identified(
    sourceIdentifier: SourceIdentifier,
    canonicalId: CanonicalId,
    sourceModifiedTime: Instant,
  ) extends WorkState {

    type WorkDataState = DataState.Identified
    type TransitionArgs = Unit

    def id = canonicalId.toString
    val relations = Relations.none

    override val modifiedTime: Instant = sourceModifiedTime
  }

  case class Merged(
    sourceIdentifier: SourceIdentifier,
    canonicalId: CanonicalId,
    mergedTime: Instant,
    sourceModifiedTime: Instant,
    availabilities: Set[Availability] = Set.empty,
  ) extends WorkState {

    type WorkDataState = DataState.Identified
    type TransitionArgs = Instant

    def id: String = canonicalId.toString
    val relations: Relations = Relations.none

    // This is used to order updates in pipeline-storage.
    // See https://github.com/wellcomecollection/docs/tree/main/rfcs/038-matcher-versioning
    override val modifiedTime: Instant = mergedTime
  }

  case class Denormalised(
    sourceIdentifier: SourceIdentifier,
    canonicalId: CanonicalId,
    mergedTime: Instant,
    sourceModifiedTime: Instant,
    availabilities: Set[Availability],
    relations: Relations = Relations.none
  ) extends WorkState {

    type WorkDataState = DataState.Identified
    type TransitionArgs = (Relations, Set[Availability])

    def id = canonicalId.toString

    // This is used to order updates in pipeline-storage.
    // See https://github.com/wellcomecollection/docs/tree/main/rfcs/038-matcher-versioning
    override val modifiedTime: Instant = mergedTime
  }

  /** Why are there three *Time parameters?
    *
    * @param mergedTime
    *   When did this Work get processed by the matcher/merger?
    *   This is used to order updates in pipeline-storage.
    *   See https://github.com/wellcomecollection/docs/tree/main/rfcs/038-matcher-versioning
    * @param sourceModifiedTime
    *   When was the underlying source record updated in the source system?
    * @param indexedTime
    *   When was this work indexed, and thus made available in the API?
    *   Combined with sourceModifiedTime, this allows us to track the latency
    *   of the pipeline.
    */
  case class Indexed(
    sourceIdentifier: SourceIdentifier,
    canonicalId: CanonicalId,
    mergedTime: Instant,
    sourceModifiedTime: Instant,
    indexedTime: Instant,
    availabilities: Set[Availability],
    derivedData: DerivedWorkData = DerivedWorkData.none,
    relations: Relations = Relations.none
  ) extends WorkState {

    type WorkDataState = DataState.Identified
    type TransitionArgs = Unit

    def id = canonicalId.toString

    override val modifiedTime: Instant = mergedTime
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
    def state(inWork: InState,
              args: OutState#TransitionArgs): OutState

    def state(inWork: InState,
              data: WorkData[InState#WorkDataState],
              args: OutState#TransitionArgs): OutState =
      state(inWork, args)

    def state(inWork: InState,
              data: Option[WorkData[InState#WorkDataState]],
              args: OutState#TransitionArgs): OutState =
      data match {
        case Some(data) => state(inWork, data, args)
        case None       => state(inWork, args)
      }

    def data(
      data: WorkData[InState#WorkDataState]): WorkData[OutState#WorkDataState]

    def data(workData: Option[WorkData[InState#WorkDataState]]): Option[WorkData[OutState#WorkDataState]] =
      workData.map(data)

    def redirect(redirect: InState#WorkDataState#Id): OutState#WorkDataState#Id
  }

  implicit val identifiedToMerged = new Transition[Identified, Merged] {
    def state(
      inWork: Identified,
      mergedTime: Instant
    ): Merged =
      Merged(
        sourceIdentifier = inWork.sourceIdentifier,
        canonicalId = inWork.canonicalId,
        mergedTime = mergedTime,
        sourceModifiedTime = inWork.sourceModifiedTime
      )

    override def state(
      inWork: Identified,
      data: WorkData[DataState.Identified],
      mergedTime: Instant): Merged =
      state(inWork, mergedTime).copy(
        availabilities = Availabilities.forWorkData(data)
      )

    def data(data: WorkData[DataState.Identified]): WorkData[DataState.Identified] = data

    def redirect(redirect: IdState.Identified): IdState.Identified = redirect
  }

  implicit val mergedToDenormalised =
    new Transition[Merged, Denormalised] {
      def state(inWork: Merged,
                context: (Relations, Set[Availability])): Denormalised =
        context match {
          case (relations, relationAvailabilities) =>
            Denormalised(
              sourceIdentifier = inWork.sourceIdentifier,
              canonicalId = inWork.canonicalId,
              mergedTime = inWork.mergedTime,
              sourceModifiedTime = inWork.sourceModifiedTime,
              availabilities = inWork.availabilities ++ relationAvailabilities,
              relations = relations
            )
        }

      def data(data: WorkData[DataState.Identified]): WorkData[DataState.Identified] = data

      def redirect(redirect: IdState.Identified): IdState.Identified = redirect
    }

  implicit val denormalisedToIndexed = new Transition[Denormalised, Indexed] {
    def state(inWork: Denormalised,
              args: Unit): Indexed =
      Indexed(
        sourceIdentifier = inWork.sourceIdentifier,
        canonicalId = inWork.canonicalId,
        mergedTime = inWork.mergedTime,
        sourceModifiedTime = inWork.sourceModifiedTime,
        indexedTime = Instant.now(),
        availabilities = inWork.availabilities,
        relations = inWork.relations
      )

    override def state(
      inWork: Denormalised,
      data: WorkData[DataState.Identified],
      args: Unit): Indexed =
      state(inWork, args).copy(
        derivedData = DerivedWorkData(data)
      )

    def data(data: WorkData[DataState.Identified]): WorkData[DataState.Identified] = data

    def redirect(redirect: IdState.Identified): IdState.Identified = redirect
  }
}
