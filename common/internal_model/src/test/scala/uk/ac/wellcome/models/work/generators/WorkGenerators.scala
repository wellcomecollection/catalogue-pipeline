package uk.ac.wellcome.models.work.generators

import java.time.Instant
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.{
  CanonicalId,
  DataState,
  SourceIdentifier
}
import weco.catalogue.internal_model.image.ImageData
import weco.catalogue.internal_model.languages.Language
import weco.catalogue.internal_model.locations.Location
import weco.catalogue.internal_model.work.DeletedReason.DeletedFromSource
import weco.catalogue.internal_model.work.WorkState._
import weco.catalogue.internal_model.work._

import scala.util.Random

trait WorkGenerators extends IdentifiersGenerators with InstantGenerators {
  private def createVersion: Int =
    Random.nextInt(100) + 1

  // To avoid having to specify a created date, it's handy having a default used in tests.
  // We can't use `Instant.now` as a default because that introduces all sorts of flakyness and race conditions.
  // So, we are introducing an arbitrary date here for convenience.
  val modifiedTime = Instant.parse("2020-10-15T15:51:00.00Z")

  def sourceWork(
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    sourceModifiedTime: Instant = instantInLast30Days
  ): Work.Visible[Source] =
    Work.Visible[Source](
      state = Source(sourceIdentifier, sourceModifiedTime),
      data = initData,
      version = createVersion
    )

  def mergedWork(
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    canonicalId: CanonicalId = createCanonicalId,
    modifiedTime: Instant = instantInLast30Days
  ): Work.Visible[Merged] = {
    val data = initData[DataState.Identified]
    Work.Visible[Merged](
      state = Merged(
        sourceIdentifier = sourceIdentifier,
        canonicalId = canonicalId,
        mergedTime = modifiedTime,
        sourceModifiedTime = modifiedTime,
        availabilities = Availabilities.forWorkData(data)),
      data = data,
      version = createVersion
    )
  }

  def denormalisedWork(
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    canonicalId: CanonicalId = createCanonicalId,
    modifiedTime: Instant = instantInLast30Days,
    relations: Relations = Relations.none
  ): Work.Visible[Denormalised] = {
    val data = initData[DataState.Identified]
    Work.Visible[Denormalised](
      state = Denormalised(
        sourceIdentifier = sourceIdentifier,
        canonicalId = canonicalId,
        mergedTime = modifiedTime,
        sourceModifiedTime = modifiedTime,
        availabilities = Availabilities.forWorkData(data),
        relations = relations
      ),
      data = data,
      version = createVersion
    )
  }

  def identifiedWork(
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    canonicalId: CanonicalId = createCanonicalId,
    sourceModifiedTime: Instant = instantInLast30Days,
  ): Work.Visible[Identified] =
    Work.Visible[Identified](
      state = Identified(
        sourceIdentifier = sourceIdentifier,
        canonicalId = canonicalId,
        sourceModifiedTime = sourceModifiedTime,
      ),
      data = initData,
      version = createVersion
    )

  def indexedWork(
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    canonicalId: CanonicalId = createCanonicalId,
    mergedTime: Instant = instantInLast30Days,
    relations: Relations = Relations.none
  ): Work.Visible[Indexed] = {
    val data = initData[DataState.Identified]
    Work.Visible[Indexed](
      state = Indexed(
        sourceIdentifier = sourceIdentifier,
        canonicalId = canonicalId,
        mergedTime = mergedTime,
        sourceModifiedTime = mergedTime,
        indexedTime = Instant.now(),
        availabilities = Availabilities.forWorkData(data),
        derivedData = DerivedWorkData(data),
        relations = relations
      ),
      data = data,
      version = createVersion
    )
  }

  def sourceWorks(count: Int): List[Work.Visible[Source]] =
    (1 to count).map(_ => sourceWork()).toList

  def mergedWorks(count: Int): List[Work.Visible[Merged]] =
    (1 to count).map(_ => mergedWork()).toList

  def denormalisedWorks(count: Int): List[Work.Visible[Denormalised]] =
    (1 to count).map(_ => denormalisedWork()).toList

  def identifiedWorks(count: Int): List[Work.Visible[Identified]] =
    (1 to count).map(_ => identifiedWork()).toList

  def indexedWorks(count: Int): List[Work.Visible[Indexed]] =
    (1 to count).map(_ => indexedWork()).toList

  implicit class WorkOps[State <: WorkState: UpdateState](
    work: Work.Visible[State]) {

    def invisible(invisibilityReasons: List[InvisibilityReason] = Nil)
      : Work.Invisible[State] =
      Work.Invisible[State](
        data = work.data,
        state = work.state,
        version = work.version,
        invisibilityReasons = invisibilityReasons
      )

    def deleted(deletedReason: DeletedReason = DeletedFromSource("tests"))
      : Work.Deleted[State] =
      Work.Deleted[State](
        state = work.state,
        data = work.data,
        version = work.version,
        deletedReason = deletedReason
      )

    def redirected(
      redirectTarget: State#WorkDataState#Id): Work.Redirected[State] =
      Work.Redirected[State](
        state = work.state,
        version = work.version,
        redirectTarget = redirectTarget
      )

    def withVersion(version: Int): Work.Visible[State] =
      Work
        .Visible[State](version = version, data = work.data, state = work.state)

    def withRedirectSources(
      redirectSources: Seq[State#WorkDataState#Id]): Work.Visible[State] =
      Work.Visible[State](
        version = work.version,
        data = work.data,
        state = work.state,
        redirectSources = redirectSources
      )

    def title(title: String): Work.Visible[State] =
      work.map(_.copy(title = Some(title)))

    def otherIdentifiers(
      otherIdentifiers: List[SourceIdentifier]): Work.Visible[State] =
      work.map(_.copy(otherIdentifiers = otherIdentifiers))

    def mergeCandidates(
      mergeCandidates: List[MergeCandidate[State#WorkDataState#Id]])
      : Work.Visible[State] =
      work.map(_.copy(mergeCandidates = mergeCandidates))

    def format(format: Format): Work.Visible[State] =
      work.map(_.copy(format = Some(format)))

    def description(description: String): Work.Visible[State] =
      work.map(_.copy(description = Some(description)))

    def physicalDescription(physicalDescription: String): Work.Visible[State] =
      work.map(_.copy(physicalDescription = Some(physicalDescription)))

    def lettering(lettering: String): Work.Visible[State] =
      work.map(_.copy(lettering = Some(lettering)))

    def createdDate(
      createdDate: Period[State#WorkDataState#MaybeId]): Work.Visible[State] =
      work.map(_.copy(createdDate = Some(createdDate)))

    def subjects(subjects: List[Subject[State#WorkDataState#MaybeId]])
      : Work.Visible[State] =
      work.map(_.copy(subjects = subjects))

    def genres(
      genres: List[Genre[State#WorkDataState#MaybeId]]): Work.Visible[State] =
      work.map(_.copy(genres = genres))

    def contributors(
      contributors: List[Contributor[State#WorkDataState#MaybeId]])
      : Work.Visible[State] =
      work.map(_.copy(contributors = contributors))

    def thumbnail(thumbnail: Location): Work.Visible[State] =
      work.map(_.copy(thumbnail = Some(thumbnail)))

    def production(
      production: List[ProductionEvent[State#WorkDataState#MaybeId]])
      : Work.Visible[State] =
      work.map(_.copy(production = production))

    def languages(languages: List[Language]): Work.Visible[State] =
      work.map(_.copy(languages = languages))

    def edition(edition: String): Work.Visible[State] =
      work.map(_.copy(edition = Some(edition)))

    def notes(notes: List[Note]): Work.Visible[State] =
      work.map(_.copy(notes = notes))

    def items(
      items: List[Item[State#WorkDataState#MaybeId]]): Work.Visible[State] =
      work.map(_.copy(items = items))

    def collectionPath(collectionPath: CollectionPath): Work.Visible[State] =
      work.map(_.copy(collectionPath = Some(collectionPath)))

    def imageData(
      imageData: List[ImageData[State#WorkDataState#Id]]): Work.Visible[State] =
      work.map(_.copy(imageData = imageData))

    def workType(workType: WorkType): Work.Visible[State] =
      work.map(_.copy(workType = workType))

    def duration(newDuration: Int): Work.Visible[State] =
      work.map(_.copy(duration = Some(newDuration)))

    def holdings(newHoldings: List[Holdings]): Work.Visible[State] =
      work.map(_.copy(holdings = newHoldings))

    def map(f: WorkData[State#WorkDataState] => WorkData[State#WorkDataState])
      : Work.Visible[State] = {
      val nextData = f(work.data)
      Work.Visible[State](
        work.version,
        nextData,
        implicitly[UpdateState[State]].apply(work.state, nextData)
      )
    }
  }

  implicit class IndexedWorkOps(work: Work.Visible[Indexed])(
    implicit updateState: UpdateState[Indexed]) {

    def ancestors(works: Work.Visible[Indexed]*): Work.Visible[Indexed] =
      Work.Visible[Indexed](
        work.version,
        work.data,
        updateState(
          work.state.copy(
            relations = work.state.relations.copy(
              ancestors = works.toList.zipWithIndex.map {
                case (work, idx) =>
                  Relation(
                    work = work,
                    depth = idx + 1,
                    numChildren = 1,
                    numDescendents = works.length - idx
                  )
              }
            )
          ),
          work.data
        )
      )
  }

  trait UpdateState[State <: WorkState] {
    def apply(state: State, data: WorkData[State#WorkDataState]): State
  }

  object UpdateState {
    def identity[State <: WorkState]: UpdateState[State] =
      (state: State, _: WorkData[State#WorkDataState]) => state

    implicit val updateIndexedState: UpdateState[Indexed] =
      (state: Indexed, data: WorkData[DataState.Identified]) =>
        state.copy(
          derivedData = DerivedWorkData(data),
          availabilities = Availabilities.forWorkData(data))
    implicit val updateIdentifiedState: UpdateState[Identified] = identity
    implicit val updateDenormalisedState: UpdateState[Denormalised] =
      (state: Denormalised, data: WorkData[DataState.Identified]) =>
        state.copy(availabilities = Availabilities.forWorkData(data))
    implicit val updateMergedState: UpdateState[Merged] =
      (state: Merged, data: WorkData[DataState.Identified]) =>
        state.copy(availabilities = Availabilities.forWorkData(data))
    implicit val updateSourceState: UpdateState[Source] = identity
  }

  private def initData[State <: DataState]: WorkData[State] =
    WorkData(
      title = Some(randomAlphanumeric(length = 10))
    )
}
