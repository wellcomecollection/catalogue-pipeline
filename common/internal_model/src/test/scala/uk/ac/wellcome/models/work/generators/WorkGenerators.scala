package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal._
import WorkState._

import scala.util.Random

trait WorkGenerators extends IdentifiersGenerators {
  private def createVersion: Int =
    Random.nextInt(100) + 1

  def chooseFrom[T](seq: T*): T =
    seq(Random.nextInt(seq.size))

  def sourceWork(
    sourceIdentifier: SourceIdentifier = createSourceIdentifier
  ): Work.Visible[Source] =
    Work.Visible[Source](
      state = Source(sourceIdentifier),
      data = initData,
      version = createVersion
    )

  def mergedWork(
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    hasMultipleSources: Boolean = false
  ): Work.Visible[Merged] =
    Work.Visible[Merged](
      state = Merged(sourceIdentifier, hasMultipleSources),
      data = initData,
      version = createVersion
    )

  def denormalisedWork(
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    hasMultipleSources: Boolean = false,
    relations: Relations[DataState.Unidentified] = Relations.none
  ): Work.Visible[Denormalised] =
    Work.Visible[Denormalised](
      state = Denormalised(sourceIdentifier, hasMultipleSources, relations),
      data = initData,
      version = createVersion
    )

  def identifiedWork(
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    canonicalId: String = createCanonicalId,
    hasMultipleSources: Boolean = chooseFrom(true, false),
    relations: Relations[DataState.Identified] = Relations.none
  ): Work.Visible[Identified] =
    Work.Visible[Identified](
      state = Identified(
        sourceIdentifier = sourceIdentifier,
        canonicalId = canonicalId,
        hasMultipleSources = hasMultipleSources,
        relations = relations
      ),
      data = initData,
      version = createVersion
    )

  def sourceWorks(count: Int): List[Work.Visible[Source]] =
    (1 to count).map(_ => sourceWork()).toList

  def mergedWorks(count: Int): List[Work.Visible[Merged]] =
    (1 to count).map(_ => mergedWork()).toList

  def denormalisedWorks(count: Int): List[Work.Visible[Denormalised]] =
    (1 to count).map(_ => denormalisedWork()).toList

  def identifiedWorks(count: Int): List[Work.Visible[Identified]] =
    (1 to count).map(_ => identifiedWork()).toList

  implicit class WorkOps[State <: WorkState](work: Work.Visible[State]) {

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

    def withVersion(version: Int): Work.Visible[State] =
      Work
        .Visible[State](version = version, data = work.data, state = work.state)

    def title(title: String): Work.Visible[State] =
      work.map(_.copy(title = Some(title)))

    def otherIdentifiers(
      otherIdentifiers: List[SourceIdentifier]): Work.Visible[State] =
      work.map(_.copy(otherIdentifiers = otherIdentifiers))

    def mergeCandidates(
      mergeCandidates: List[MergeCandidate]): Work.Visible[State] =
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

    def thumbnail(thumbnail: LocationDeprecated): Work.Visible[State] =
      work.map(_.copy(thumbnail = Some(thumbnail)))

    def production(
      production: List[ProductionEvent[State#WorkDataState#MaybeId]])
      : Work.Visible[State] =
      work.map(_.copy(production = production))

    def language(language: Language): Work.Visible[State] =
      work.map(_.copy(language = Some(language)))

    def edition(edition: String): Work.Visible[State] =
      work.map(_.copy(edition = Some(edition)))

    def notes(notes: List[Note]): Work.Visible[State] =
      work.map(_.copy(notes = notes))

    def items(
      items: List[Item[State#WorkDataState#MaybeId]]): Work.Visible[State] =
      work.map(_.copy(items = items))

    def collectionPath(collectionPath: CollectionPath): Work.Visible[State] =
      work.map(_.copy(collectionPath = Some(collectionPath)))

    def images(
      images: List[UnmergedImage[State#WorkDataState]]): Work.Visible[State] =
      work.map(_.copy(images = images))

    def workType(workType: WorkType): Work.Visible[State] =
      work.map(_.copy(workType = workType))

    def duration(newDuration: Int): Work.Visible[State] =
      work.map(_.copy(duration = Some(newDuration)))

    def map(f: WorkData[State#WorkDataState] => WorkData[State#WorkDataState])
      : Work.Visible[State] =
      Work.Visible[State](work.version, f(work.data), work.state)
  }

  private def initData[State <: DataState]: WorkData[State] =
    WorkData(
      title = Some(randomAlphanumeric(length = 10))
    )
}
