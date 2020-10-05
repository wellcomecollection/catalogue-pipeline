package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal._

trait LegacyWorkGenerators
    extends ItemsGenerators
    with ProductionEventGenerators {

  import WorkState._

  private def createTitle: String = randomAlphanumeric(length = 100)

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
}
