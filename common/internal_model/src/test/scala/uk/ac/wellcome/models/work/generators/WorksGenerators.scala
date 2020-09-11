package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal._

trait WorksGenerators extends ItemsGenerators with ProductionEventGenerators {

  import WorkState._

  private def createTitle: String = randomAlphanumeric(length = 100)

  def createUnidentifiedRedirectedWork: Work.Redirected[Unidentified] =
    Work.Redirected(
      state = Unidentified(createSourceIdentifier),
      version = 1,
      redirect = IdState.Identifiable(createSourceIdentifier)
    )

  def createRedirectedWork[State <: WorkState](
    source: Work[State],
    target: Work[State]): Work.Redirected[State] =
    Work.Redirected(
      state = source.state,
      version = source.version,
      redirect = IdState.Identifiable(target.sourceIdentifier)
    )

  def createUnidentifiedRedirectedWorkWith(
    redirect: IdState.Identifiable): Work.Redirected[Unidentified] =
    Work.Redirected(
      state = Unidentified(createSourceIdentifier),
      version = 1,
      redirect = redirect
    )

  def createIdentifiedRedirectedWork: Work.Redirected[Identified] =
    createIdentifiedRedirectedWorkWith()

  def createIdentifiedRedirectedWorkWith(
    canonicalId: String = createCanonicalId,
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    version: Int = 1,
  ): Work.Redirected[Identified] =
    Work.Redirected(
      state = Identified(
        canonicalId = canonicalId,
        sourceIdentifier = sourceIdentifier,
      ),
      version = version,
      redirect = IdState.Identified(
        canonicalId = createCanonicalId,
        sourceIdentifier = createSourceIdentifier
      )
    )

  def createUnidentifiedInvisibleWorkWith(
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    items: List[Item[IdState.Unminted]] = Nil,
    images: List[UnmergedImage[IdState.Identifiable, WorkState.Unidentified]] = Nil,
  ): Work.Invisible[Unidentified] =
    UnidentifiedInvisibleWork(
      sourceIdentifier = sourceIdentifier,
      data = WorkData(items = items, images = images),
      version = 1
    )

  def createUnidentifiedInvisibleWork: UnidentifiedInvisibleWork =
    createUnidentifiedInvisibleWorkWith()

  def createIdentifiedInvisibleWorkWith(
    canonicalId: String = createCanonicalId,
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    version: Int = 1
  ): IdentifiedInvisibleWork =
    IdentifiedInvisibleWork(
      sourceIdentifier = sourceIdentifier,
      version = version,
      canonicalId = canonicalId,
      data = WorkData()
    )

  def createIdentifiedInvisibleWork: IdentifiedInvisibleWork =
    createIdentifiedInvisibleWorkWith()

  def createIdentifiedInvisibleWorks(count: Int): Seq[IdentifiedInvisibleWork] =
    (1 to count).map { _ =>
      createIdentifiedInvisibleWork
    }

  def createUnidentifiedWorkWith(
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    version: Int = 1,
    title: Option[String] = Some(createTitle),
    alternativeTitles: List[String] = Nil,
    otherIdentifiers: List[SourceIdentifier] = List(),
    mergeCandidates: List[MergeCandidate] = List(),
    description: Option[String] = None,
    physicalDescription: Option[String] = None,
    lettering: Option[String] = None,
    workType: Option[WorkType] = None,
    thumbnail: Option[LocationDeprecated] = None,
    contributors: List[Contributor[IdState.Unminted]] = Nil,
    production: List[ProductionEvent[IdState.Unminted]] = Nil,
    notes: List[Note] = Nil,
    edition: Option[String] = None,
    duration: Option[Int] = None,
    items: List[Item[IdState.Unminted]] = Nil,
    images: List[UnmergedImage[IdState.Identifiable, IdState.Unminted]] = Nil)
    : UnidentifiedWork =
    UnidentifiedWork(
      sourceIdentifier = sourceIdentifier,
      version = version,
      data = WorkData(
        otherIdentifiers = otherIdentifiers,
        mergeCandidates = mergeCandidates,
        title = title,
        alternativeTitles = alternativeTitles,
        workType = workType,
        description = description,
        physicalDescription = physicalDescription,
        lettering = lettering,
        contributors = contributors,
        thumbnail = thumbnail,
        production = production,
        edition = edition,
        notes = notes,
        duration = duration,
        items = items,
        images = images
      )
    )

  def createUnidentifiedWork: UnidentifiedWork = createUnidentifiedWorkWith()

  def createUnidentifiedWorks(count: Int): Seq[UnidentifiedWork] =
    (1 to count).map { _ =>
      createUnidentifiedWork
    }

  def createIdentifiedWorkWith(
    canonicalId: String = createCanonicalId,
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    otherIdentifiers: List[SourceIdentifier] = List(),
    title: Option[String] = Some(createTitle),
    alternativeTitles: List[String] = Nil,
    workType: Option[WorkType] = None,
    description: Option[String] = None,
    physicalDescription: Option[String] = None,
    lettering: Option[String] = None,
    createdDate: Option[Period[IdState.Minted]] = None,
    subjects: List[Subject[IdState.Minted]] = Nil,
    genres: List[Genre[IdState.Minted]] = Nil,
    contributors: List[Contributor[IdState.Minted]] = Nil,
    thumbnail: Option[LocationDeprecated] = None,
    production: List[ProductionEvent[IdState.Minted]] = Nil,
    notes: List[Note] = Nil,
    edition: Option[String] = None,
    language: Option[Language] = None,
    duration: Option[Int] = None,
    items: List[Item[IdState.Minted]] = Nil,
    images: List[UnmergedImage[IdState.Identified, IdState.Minted]] = Nil,
    version: Int = 1,
    merged: Boolean = false,
    collectionPath: Option[CollectionPath] = None,
    mergeCandidates: List[MergeCandidate] = Nil
  ): IdentifiedWork =
    IdentifiedWork(
      canonicalId = canonicalId,
      sourceIdentifier = sourceIdentifier,
      version = version,
      data = WorkData(
        otherIdentifiers = otherIdentifiers,
        mergeCandidates = mergeCandidates,
        title = title,
        alternativeTitles = alternativeTitles,
        workType = workType,
        description = description,
        physicalDescription = physicalDescription,
        lettering = lettering,
        createdDate = createdDate,
        subjects = subjects,
        genres = genres,
        contributors = contributors,
        thumbnail = thumbnail,
        production = production,
        language = language,
        edition = edition,
        notes = notes,
        duration = duration,
        items = items,
        images = images,
        merged = merged,
        collectionPath = collectionPath,
      )
    )

  def createIdentifiedWork: IdentifiedWork = createIdentifiedWorkWith()

  def createIdentifiedWorks(count: Int): Seq[IdentifiedWork] =
    (1 to count).map { _ =>
      createIdentifiedWork
    }

  def createUnidentifiedSierraWorkWith(
    workType: Option[WorkType] = None,
    items: List[Item[IdState.Unminted]] = Nil,
    mergeCandidates: List[MergeCandidate] = Nil,
  ): UnidentifiedWork =
    createUnidentifiedWorkWith(
      sourceIdentifier = createSierraSystemSourceIdentifier,
      workType = workType,
      otherIdentifiers = List(createSierraSystemSourceIdentifier),
      items = items,
      mergeCandidates = mergeCandidates
    )

  def createIdentifiedSierraWorkWith(
    workType: Option[WorkType] = None,
    items: List[Item[IdState.Minted]] = Nil,
    mergeCandidates: List[MergeCandidate] = Nil,
  ): IdentifiedWork =
    createIdentifiedWorkWith(
      sourceIdentifier = createSierraSystemSourceIdentifier,
      workType = workType,
      otherIdentifiers = List(createSierraSystemSourceIdentifier),
      items = items,
      mergeCandidates = mergeCandidates
    )

  def createUnidentifiedCalmWorkWith(
    data: WorkData[IdState.Unminted, IdState.Identifiable] = WorkData(
      items = List(createCalmItem)
    ),
    id: String = randomAlphanumeric(6),
    version: Int = 0) =
    UnidentifiedWork(
      sourceIdentifier = SourceIdentifier(
        value = id,
        identifierType = IdentifierType("calm-record-id"),
      ),
      version = version,
      data = data,
    )

  val createUnidentifiedCalmWork = createUnidentifiedCalmWorkWith()

  def createUnidentifiedInvisibleMetsWorkWith(
    sourceIdentifier: SourceIdentifier = createMetsSourceIdentifier,
    items: List[Item[IdState.Unminted]] = List(createDigitalItem),
    images: List[UnmergedImage[IdState.Identifiable, IdState.Unminted]])
    : UnidentifiedInvisibleWork =
    createUnidentifiedInvisibleWorkWith(
      sourceIdentifier = sourceIdentifier,
      items = items,
      images = images
    )

  def createUnidentifiedSierraWork: UnidentifiedWork =
    createUnidentifiedSierraWorkWith()

  def createSierraPhysicalWork: UnidentifiedWork =
    createUnidentifiedSierraWorkWith(items = List(createPhysicalItem))

  def createSierraWorkWithDigitisedMergeCandidate = {
    val physicalSierraWork = createSierraPhysicalWork
    val digitisedCopyOfSierraWork = createUnidentifiedSierraWork
    val physicalSierraWorkWithMergeCandidate = physicalSierraWork.copy(
      data = physicalSierraWork.data.copy(
        mergeCandidates = List(
          MergeCandidate(
            identifier = digitisedCopyOfSierraWork.sourceIdentifier,
            reason = Some("Physical/digitised Sierra work")
          ))))

    (physicalSierraWorkWithMergeCandidate, digitisedCopyOfSierraWork)
  }

  def createSierraDigitalWork: UnidentifiedWork =
    createSierraDigitalWorkWith()

  def createSierraWorkWithTwoPhysicalItems =
    createUnidentifiedSierraWorkWith(
      items = List(createPhysicalItem, createPhysicalItem)
    )

  def createSierraDigitalWorkWith(
    items: List[Item[IdState.Unminted]] = List(
      createUnidentifiableItemWith(locations = List(createDigitalLocation))))
    : UnidentifiedWork =
    createUnidentifiedSierraWorkWith(items = items)

  def createMiroWorkWith(
    images: List[UnmergedImage[IdState.Identifiable, IdState.Unminted]],
    otherIdentifiers: List[SourceIdentifier] = Nil,
    sourceIdentifier: SourceIdentifier = createMiroSourceIdentifier)
    : UnidentifiedWork =
    createUnidentifiedWorkWith(
      sourceIdentifier = sourceIdentifier,
      otherIdentifiers = otherIdentifiers,
      thumbnail = Some(
        DigitalLocationDeprecated(
          url = "https://iiif.wellcomecollection.org/V01234.jpg",
          locationType = LocationType("thumbnail-image"),
          license = Some(License.CCBY)
        )),
      items = List(
        createUnidentifiableItemWith(locations = List(
          createDigitalLocationWith(locationType = createImageLocationType)))),
      images = images
    )

  def createIsbnWork: UnidentifiedWork =
    createUnidentifiedWorkWith(
      sourceIdentifier = createIsbnSourceIdentifier,
    )

  def createIsbnWorks(count: Int): List[UnidentifiedWork] =
    List.fill(count)(createIsbnWork)

  def createDatedWork(
    dateLabel: String,
    canonicalId: String = createCanonicalId
  ): IdentifiedWork =
    createIdentifiedWorkWith(
      canonicalId = canonicalId,
      production = List(createProductionEventWith(dateLabel = Some(dateLabel)))
    )

  def createLicensedWork(canonicalId: String,
                         licenses: List[License]): IdentifiedWork =
    createIdentifiedWorkWith(
      canonicalId = canonicalId,
      items = licenses.map { license =>
        createDigitalItemWith(license = Some(license))
      }
    )
}
