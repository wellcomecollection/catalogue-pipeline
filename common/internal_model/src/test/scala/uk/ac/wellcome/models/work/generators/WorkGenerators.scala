package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal._
import WorkState._

trait WorkGenerators extends IdentifiersGenerators {

  def sourceWork(sourceIdentifier: SourceIdentifier = createSourceIdentifier)
    : Work.Visible[Source] =
    Work.Visible[Source](
      state = Source(sourceIdentifier),
      data = WorkData[DataState.Unidentified](),
      version = 1
    )

  def mergedWork(
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    hasMultipleSources: Boolean = false
  ): Work.Visible[Merged] =
    Work.Visible[Merged](
      state = Merged(sourceIdentifier, hasMultipleSources),
      data = WorkData[DataState.Unidentified](),
      version = 1
    )

  def denormalisedWork(
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    hasMultipleSources: Boolean = false,
    relations: Relations[DataState.Unidentified] = Relations.none
  ): Work.Visible[Denormalised] =
    Work.Visible[Denormalised](
      state = Denormalised(sourceIdentifier, hasMultipleSources, relations),
      data = WorkData[DataState.Unidentified](),
      version = 1
    )

  def identifiedWork(
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    canonicalId: String = createCanonicalId,
    hasMultipleSources: Boolean = false,
    relations: Relations[DataState.Identified] = Relations.none
  ): Work.Visible[Identified] =
    Work.Visible[Identified](
      state = Identified(
        sourceIdentifier,
        canonicalId,
        hasMultipleSources,
        relations),
      data = WorkData[DataState.Identified](),
      version = 1
    )

  implicit class WorkOps[State <: WorkState](work: Work[State]) {

    def invisible(invisibilityReasons: List[InvisibilityReason] = Nil)
      : Work.Invisible[State] =
      Work.Invisible[State](
        data = work.data,
        state = work.state,
        version = work.version,
        invisibilityReasons = invisibilityReasons
      )

    def redirected(redirect: State#WorkDataState#Id): Work.Redirected[State] =
      Work.Redirected[State](
        state = work.state,
        version = work.version,
        redirect = redirect
      )

    def version(version: Int): Work[State] =
      work match {
        case Work.Visible(_, data, state) =>
          Work.Visible[State](version, data, state)
        case Work.Invisible(_, data, state, invisibilityReasons) =>
          Work.Invisible[State](version, data, state, invisibilityReasons)
        case Work.Redirected(_, redirect, state) =>
          Work.Redirected[State](version, redirect, state)
      }

    def title(title: String): Work[State] =
      work.mapData(_.copy(title = Some(title)))

    def otherIdentifiers(
      otherIdentifiers: List[SourceIdentifier]): Work[State] =
      work.mapData(_.copy(otherIdentifiers = otherIdentifiers))

    def mergeCandidates(mergeCandidates: List[MergeCandidate]): Work[State] =
      work.mapData(_.copy(mergeCandidates = mergeCandidates))

    def format(format: Format): Work[State] =
      work.mapData(_.copy(format = Some(format)))

    def description(description: String): Work[State] =
      work.mapData(_.copy(description = Some(description)))

    def physicalDescription(physicalDescription: String): Work[State] =
      work.mapData(_.copy(physicalDescription = Some(physicalDescription)))

    def subjects(
      subjects: List[Subject[State#WorkDataState#MaybeId]]): Work[State] =
      work.mapData(_.copy(subjects = subjects))

    def genres(genres: List[Genre[State#WorkDataState#MaybeId]]): Work[State] =
      work.mapData(_.copy(genres = genres))

    def contributors(
      contributors: List[Contributor[State#WorkDataState#MaybeId]])
      : Work[State] =
      work.mapData(_.copy(contributors = contributors))

    def thumbnail(thumbnail: LocationDeprecated): Work[State] =
      work.mapData(_.copy(thumbnail = Some(thumbnail)))

    def production(
      production: List[ProductionEvent[State#WorkDataState#MaybeId]])
      : Work[State] =
      work.mapData(_.copy(production = production))

    def language(language: Language): Work[State] =
      work.mapData(_.copy(language = Some(language)))

    def edition(edition: String): Work[State] =
      work.mapData(_.copy(edition = Some(edition)))

    def notes(notes: List[Note]): Work[State] =
      work.mapData(_.copy(notes = notes))

    def items(items: List[Item[State#WorkDataState#MaybeId]]): Work[State] =
      work.mapData(_.copy(items = items))

    def collectionPath(collectionPath: CollectionPath): Work[State] =
      work.mapData(_.copy(collectionPath = Some(collectionPath)))

    def images(images: List[UnmergedImage[State#WorkDataState]]): Work[State] =
      work.mapData(_.copy(images = images))

    def workType(workType: WorkType): Work[State] =
      work.mapData(_.copy(workType = workType))
  }
}
