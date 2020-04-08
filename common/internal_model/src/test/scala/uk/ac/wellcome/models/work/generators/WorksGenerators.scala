package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal._

trait WorksGenerators
    extends ItemsGenerators
    with ProductionEventGenerators
    with ImageGenerators {

  private def createTitle: String = randomAlphanumeric(length = 100)

  def createUnidentifiedRedirectedWork: UnidentifiedRedirectedWork =
    UnidentifiedRedirectedWork(
      sourceIdentifier = createSourceIdentifier,
      version = 1,
      redirect = IdentifiableRedirect(
        sourceIdentifier = createSourceIdentifier
      )
    )

  def createUnidentifiedRedirectedWork(
    source: TransformedBaseWork,
    target: UnidentifiedWork): UnidentifiedRedirectedWork =
    UnidentifiedRedirectedWork(
      sourceIdentifier = source.sourceIdentifier,
      version = source.version,
      redirect = IdentifiableRedirect(target.sourceIdentifier)
    )

  def createUnidentifiedRedirectedWorkWith(
    redirect: IdentifiableRedirect): UnidentifiedRedirectedWork =
    UnidentifiedRedirectedWork(
      sourceIdentifier = createSourceIdentifier,
      version = 1,
      redirect = redirect
    )

  def createIdentifiedRedirectedWork: IdentifiedRedirectedWork =
    createIdentifiedRedirectedWorkWith()

  def createIdentifiedRedirectedWorkWith(
    canonicalId: String = createCanonicalId,
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    version: Int = 1,
  ): IdentifiedRedirectedWork =
    IdentifiedRedirectedWork(
      canonicalId = canonicalId,
      sourceIdentifier = sourceIdentifier,
      version = version,
      redirect = IdentifiedRedirect(
        canonicalId = createCanonicalId
      )
    )

  def createUnidentifiedInvisibleWorkWith(
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    items: List[Item[Unminted]] = Nil,
    images: List[UnmergedImage[Identifiable]] = Nil,
  ): UnidentifiedInvisibleWork =
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
    thumbnail: Option[Location] = None,
    contributors: List[Contributor[Unminted]] = Nil,
    production: List[ProductionEvent[Unminted]] = Nil,
    notes: List[Note] = Nil,
    edition: Option[String] = None,
    duration: Option[Int] = None,
    items: List[Item[Unminted]] = Nil,
    images: List[UnmergedImage[Identifiable]] = Nil): UnidentifiedWork =
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
    createdDate: Option[Period[Minted]] = None,
    subjects: List[Subject[Minted]] = Nil,
    genres: List[Genre[Minted]] = Nil,
    contributors: List[Contributor[Minted]] = Nil,
    thumbnail: Option[Location] = None,
    production: List[ProductionEvent[Minted]] = Nil,
    notes: List[Note] = Nil,
    edition: Option[String] = None,
    language: Option[Language] = None,
    duration: Option[Int] = None,
    items: List[Item[Minted]] = Nil,
    version: Int = 1,
    merged: Boolean = false,
    collectionPath: Option[CollectionPath] = None,
  ): IdentifiedWork =
    IdentifiedWork(
      canonicalId = canonicalId,
      sourceIdentifier = sourceIdentifier,
      version = version,
      data = WorkData(
        otherIdentifiers = otherIdentifiers,
        mergeCandidates = List(),
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
    items: List[Item[Unminted]] = Nil): UnidentifiedWork =
    createUnidentifiedWorkWith(
      sourceIdentifier = createSierraSystemSourceIdentifier,
      workType = workType,
      otherIdentifiers = List(createSierraSystemSourceIdentifier),
      items = items
    )

  def createUnidentifiedCalmWork(data: WorkData[Unminted, Identifiable] = WorkData(
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

  def createUnidentifiedInvisibleMetsWorkWith(
    sourceIdentifier: SourceIdentifier = createMetsSourceIdentifier,
    items: List[Item[Unminted]] = List(createDigitalItem),
    numImages: Int = 1): UnidentifiedInvisibleWork =
    createUnidentifiedInvisibleWorkWith(
      sourceIdentifier = createMetsSourceIdentifier,
      items = items,
      images = (1 to numImages).map { _ =>
        createUnmergedImageWith(
          location = createDigitalLocation,
          identifierType = IdentifierType("mets-image")
        )
      }.toList
    )

  def createUnidentifiedSierraWork: UnidentifiedWork =
    createUnidentifiedSierraWorkWith()

  def createSierraPhysicalWork: UnidentifiedWork =
    createUnidentifiedSierraWorkWith(items = List(createPhysicalItem))

  def createSierraDigitalWork: UnidentifiedWork =
    createSierraDigitalWorkWith()

  def createSierraWorkWithTwoPhysicalItems =
    createUnidentifiedSierraWorkWith(
      items = List(createPhysicalItem, createPhysicalItem)
    )

  def createSierraDigitalWorkWith(
    items: List[Item[Unminted]] = List(
      createUnidentifiableItemWith(locations = List(createDigitalLocation))))
    : UnidentifiedWork =
    createUnidentifiedSierraWorkWith(items = items)

  def createUnidentifiedInvisibleMetsWork: UnidentifiedInvisibleWork =
    createUnidentifiedInvisibleMetsWorkWith()

  def createMiroWorkWith(
    otherIdentifiers: List[SourceIdentifier] = List()): UnidentifiedWork =
    createUnidentifiedWorkWith(
      sourceIdentifier = createMiroSourceIdentifier,
      otherIdentifiers = otherIdentifiers,
      thumbnail = Some(
        DigitalLocation(
          url = "https://iiif.wellcomecollection.org/V01234.jpg",
          locationType = LocationType("thumbnail-image"),
          license = Some(License.CCBY)
        )),
      items = List(
        createUnidentifiableItemWith(locations = List(
          createDigitalLocationWith(locationType = createImageLocationType)))),
      images = List(
        createUnmergedImageWith(
          location = DigitalLocation(
            url = "https://iiif.wellcomecollection.org/V01234.jpg",
            locationType = LocationType("iiif-image"),
            license = Some(License.CCBY)
          )
        ))
    )

  def createMiroWork: UnidentifiedWork =
    createMiroWorkWith()

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
