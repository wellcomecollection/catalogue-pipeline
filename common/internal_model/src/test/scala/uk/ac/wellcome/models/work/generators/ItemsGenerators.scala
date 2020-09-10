package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal.{DigitalLocationDeprecated, _}

trait ItemsGenerators extends IdentifiersGenerators {

  def createIdentifiedItemWith[Id >: Identified](
    canonicalId: String = createCanonicalId,
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    otherIdentifiers: List[SourceIdentifier] = Nil,
    locations: List[LocationDeprecated] = List(defaultLocation),
    title: Option[String] = None,
  ): Item[Id] =
    Item(
      id = Identified(
        canonicalId = canonicalId,
        sourceIdentifier = sourceIdentifier,
        otherIdentifiers = otherIdentifiers,
      ),
      locationsDeprecated = locations,
      title = title,
    )

  def createIdentifiedItem = createIdentifiedItemWith()

  def createIdentifiedItems(count: Int) =
    (1 to count).map { _ =>
      createIdentifiedItem
    }.toList

  def createIdentifiableItemWith[Id >: Identifiable](
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    locations: List[LocationDeprecated] = List(defaultLocation)
  ): Item[Id] =
    Item(
      id = Identifiable(sourceIdentifier),
      locationsDeprecated = locations
    )

  def createUnidentifiableItemWith[Id >: Unidentifiable.type](
    locations: List[LocationDeprecated] = List(defaultLocation)): Item[Id] =
    Item(id = Unidentifiable, locationsDeprecated = locations)

  def createPhysicalLocation = createPhysicalLocationWith()

  def createPhysicalLocationWith(locationType: LocationType =
                                   createStoresLocationType,
                                 label: String = "locationLabel") =
    PhysicalLocationDeprecated(locationType, label)

  def createDigitalLocation = createDigitalLocationWith()

  def createDigitalLocationWith(
    locationType: LocationType = createPresentationLocationType,
    url: String = defaultLocationUrl,
    license: Option[License] = Some(License.CCBY),
    accessConditions: List[AccessCondition] = Nil) = DigitalLocationDeprecated(
    locationType = locationType,
    url = url,
    license = license,
    accessConditions = accessConditions
  )

  def createImageLocationType = LocationType("iiif-image")

  def createPresentationLocationType = LocationType("iiif-presentation")

  def createStoresLocationType = LocationType("sgmed")

  def createPhysicalItem =
    createIdentifiableItemWith(locations = List(createPhysicalLocation))

  def createDigitalItem =
    createUnidentifiableItemWith(locations = List(createDigitalLocation))

  def createDigitalItemWith(locations: List[LocationDeprecated]) =
    createUnidentifiableItemWith(locations = locations)

  def createDigitalItemWith(license: Option[License]) =
    createUnidentifiableItemWith(
      locations = List(createDigitalLocationWith(license = license))
    )

  def createCalmItem =
    createUnidentifiableItemWith(
      locations = List(
        createPhysicalLocationWith(
          locationType = LocationType("scmac"),
          label = "Closed stores Arch. & MSS",
        )
      )
    )

  private def defaultLocation = createDigitalLocationWith()

  private def defaultLocationUrl =
    s"https://iiif.wellcomecollection.org/image/${randomAlphanumeric(3)}.jpg/info.json"
}
