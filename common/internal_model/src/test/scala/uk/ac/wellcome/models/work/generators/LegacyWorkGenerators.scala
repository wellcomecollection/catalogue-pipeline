package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal._

trait LegacyWorkGenerators
    extends ItemsGenerators
    with ProductionEventGenerators {

  import WorkState._

  private def createTitle: String = randomAlphanumeric(length = 100)

  def createIdentifiedRedirectedWork: Work.Redirected[Identified] =
    createIdentifiedRedirectedWorkWith()

  def createIdentifiedRedirectedWorkWith(
    canonicalId: String = createCanonicalId,
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    version: Int = 1,
  ): Work.Redirected[Identified] =
    Work.Redirected[Identified](
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

  def createInvisibleSourceWorkWith(
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    items: List[Item[IdState.Unminted]] = Nil,
    images: List[UnmergedImage[DataState.Unidentified]] = Nil,
  ): Work.Invisible[Source] =
    Work.Invisible[Source](
      state = Source(sourceIdentifier),
      data = WorkData[DataState.Unidentified](
        items = items,
        images = images,
      ),
      version = 1
    )

  def createInvisibleSourceWork: Work.Invisible[Source] =
    createInvisibleSourceWorkWith()

  def createIdentifiedInvisibleWorkWith(
    canonicalId: String = createCanonicalId,
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    version: Int = 1
  ): Work.Invisible[Identified] =
    Work.Invisible(
      state = Identified(
        sourceIdentifier = sourceIdentifier,
        canonicalId = canonicalId,
      ),
      version = version,
      data = WorkData()
    )

  def createIdentifiedInvisibleWork: Work.Invisible[Identified] =
    createIdentifiedInvisibleWorkWith()

  def createIdentifiedInvisibleWorks(
    count: Int): Seq[Work.Invisible[Identified]] =
    (1 to count).map { _ =>
      createIdentifiedInvisibleWork
    }

  def createSourceWorkWith(
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    version: Int = 1,
    title: Option[String] = Some(createTitle),
    alternativeTitles: List[String] = Nil,
    otherIdentifiers: List[SourceIdentifier] = List(),
    mergeCandidates: List[MergeCandidate] = List(),
    description: Option[String] = None,
    physicalDescription: Option[String] = None,
    lettering: Option[String] = None,
    format: Option[Format] = None,
    thumbnail: Option[LocationDeprecated] = None,
    contributors: List[Contributor[IdState.Unminted]] = Nil,
    production: List[ProductionEvent[IdState.Unminted]] = Nil,
    notes: List[Note] = Nil,
    edition: Option[String] = None,
    duration: Option[Int] = None,
    items: List[Item[IdState.Unminted]] = Nil,
    images: List[UnmergedImage[DataState.Unidentified]] = Nil)
    : Work.Visible[Source] =
    Work.Visible[Source](
      state = Source(sourceIdentifier),
      version = version,
      data = WorkData[DataState.Unidentified](
        otherIdentifiers = otherIdentifiers,
        mergeCandidates = mergeCandidates,
        title = title,
        alternativeTitles = alternativeTitles,
        format = format,
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

  def createSourceWork: Work.Visible[Source] =
    createSourceWorkWith()

  def createSourceWorks(count: Int): Seq[Work.Visible[Source]] =
    (1 to count).map { _ =>
      createSourceWork
    }

  def createIdentifiedWorkWith(
    canonicalId: String = createCanonicalId,
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    otherIdentifiers: List[SourceIdentifier] = List(),
    title: Option[String] = Some(createTitle),
    alternativeTitles: List[String] = Nil,
    format: Option[Format] = None,
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
    images: List[UnmergedImage[DataState.Identified]] = Nil,
    version: Int = 1,
    merged: Boolean = false,
    collectionPath: Option[CollectionPath] = None,
    mergeCandidates: List[MergeCandidate] = Nil,
    workType: WorkType = WorkType.Standard,
  ): Work.Visible[Identified] =
    Work.Visible[Identified](
      state = Identified(
        canonicalId = canonicalId,
        sourceIdentifier = sourceIdentifier,
        hasMultipleSources = merged
      ),
      version = version,
      data = WorkData[DataState.Identified](
        otherIdentifiers = otherIdentifiers,
        mergeCandidates = mergeCandidates,
        title = title,
        alternativeTitles = alternativeTitles,
        format = format,
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
        collectionPath = collectionPath,
        workType = workType,
      )
    )

  def createIdentifiedWork: Work.Visible[Identified] =
    createIdentifiedWorkWith()

  def createIdentifiedWorks(count: Int): Seq[Work.Visible[Identified]] =
    (1 to count).map { _ =>
      createIdentifiedWork
    }

  def createSierraSourceWorkWith(
    format: Option[Format] = None,
    items: List[Item[IdState.Unminted]] = Nil,
    mergeCandidates: List[MergeCandidate] = Nil,
  ): Work.Visible[Source] =
    createSourceWorkWith(
      sourceIdentifier = createSierraSystemSourceIdentifier,
      format = format,
      otherIdentifiers = List(createSierraSystemSourceIdentifier),
      items = items,
      mergeCandidates = mergeCandidates
    )

  def createIdentifiedSierraWorkWith(
    format: Option[Format] = None,
    items: List[Item[IdState.Minted]] = Nil,
    mergeCandidates: List[MergeCandidate] = Nil,
  ): Work.Visible[Identified] =
    createIdentifiedWorkWith(
      sourceIdentifier = createSierraSystemSourceIdentifier,
      format = format,
      otherIdentifiers = List(createSierraSystemSourceIdentifier),
      items = items,
      mergeCandidates = mergeCandidates
    )

  def createCalmSourceWorkWith(data: WorkData[DataState.Unidentified] =
                                 WorkData[DataState.Unidentified](
                                   items = List(createCalmItem)
                                 ),
                               id: String = randomAlphanumeric(6),
                               version: Int = 0): Work.Visible[Source] =
    Work.Visible[Source](
      state = Source(
        sourceIdentifier = SourceIdentifier(
          value = id,
          identifierType = IdentifierType("calm-record-id"),
        ),
      ),
      version = version,
      data = data,
    )

  val createCalmSourceWork = createCalmSourceWorkWith()

  def createInvisibleMetsSourceWorkWith(
    sourceIdentifier: SourceIdentifier = createMetsSourceIdentifier,
    items: List[Item[IdState.Unminted]] = List(createDigitalItem),
    images: List[UnmergedImage[DataState.Unidentified]])
    : Work.Invisible[Source] =
    createInvisibleSourceWorkWith(
      sourceIdentifier = sourceIdentifier,
      items = items,
      images = images
    )

  def createSierraSourceWork: Work.Visible[Source] =
    createSierraSourceWorkWith()

  def createSierraPhysicalWork: Work.Visible[Source] =
    createSierraSourceWorkWith(items = List(createPhysicalItem))

  def createSierraWorkWithDigitisedMergeCandidate = {
    val physicalSierraWork = createSierraPhysicalWork
    val digitisedCopyOfSierraWork = createSierraSourceWork
    val physicalSierraWorkWithMergeCandidate = physicalSierraWork.copy(
      data = physicalSierraWork.data.copy(
        mergeCandidates = List(
          MergeCandidate(
            identifier = digitisedCopyOfSierraWork.sourceIdentifier,
            reason = Some("Physical/digitised Sierra work")
          ))))

    (physicalSierraWorkWithMergeCandidate, digitisedCopyOfSierraWork)
  }

  def createSierraDigitalWork: Work.Visible[Source] =
    createSierraDigitalWorkWith()

  def createSierraWorkWithTwoPhysicalItems =
    createSierraSourceWorkWith(
      items = List(createPhysicalItem, createPhysicalItem)
    )

  def createSierraDigitalWorkWith(
    items: List[Item[IdState.Unminted]] = List(
      createUnidentifiableItemWith(locations = List(createDigitalLocation))))
    : Work.Visible[Source] =
    createSierraSourceWorkWith(items = items)

  def createMiroWorkWith(images: List[UnmergedImage[DataState.Unidentified]],
                         otherIdentifiers: List[SourceIdentifier] = Nil,
                         sourceIdentifier: SourceIdentifier =
                           createMiroSourceIdentifier): Work.Visible[Source] =
    createSourceWorkWith(
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

  def createIsbnWork: Work.Visible[Source] =
    createSourceWorkWith(
      sourceIdentifier = createIsbnSourceIdentifier,
    )

  def createIsbnWorks(count: Int): List[Work.Visible[Source]] =
    List.fill(count)(createIsbnWork)

  def createDatedWork(
    dateLabel: String,
    canonicalId: String = createCanonicalId
  ): Work.Visible[Identified] =
    createIdentifiedWorkWith(
      canonicalId = canonicalId,
      production = List(createProductionEventWith(dateLabel = Some(dateLabel)))
    )

  def createLicensedWork(canonicalId: String,
                         licenses: List[License]): Work.Visible[Identified] =
    createIdentifiedWorkWith(
      canonicalId = canonicalId,
      items = licenses.map { license =>
        createDigitalItemWith(license = Some(license))
      }
    )

  def createPhysicalWork(canonicalId: String = createCanonicalId) =
    createIdentifiedWorkWith(
      canonicalId,
      items = List(
        createIdentifiedItemWith(locations = List(createPhysicalLocation))
      )
    )

  def createDigitalWork(canonicalId: String = createCanonicalId) =
    createIdentifiedWorkWith(
      canonicalId,
      items = List(
        createIdentifiedItemWith(locations = List(createDigitalLocation))
      )
    )
}
