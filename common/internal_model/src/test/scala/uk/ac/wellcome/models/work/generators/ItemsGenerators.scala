package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal.{DigitalLocation, _}

trait ItemsGenerators extends IdentifiersGenerators {

  def createIdentifiedItemWith[I >: IdState.Identified](
    canonicalId: String = createCanonicalId,
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    otherIdentifiers: List[SourceIdentifier] = Nil,
    locations: List[Location] = List(defaultLocation),
    title: Option[String] = None,
  ): Item[I] =
    Item(
      id = IdState.Identified(
        canonicalId = canonicalId,
        sourceIdentifier = sourceIdentifier,
        otherIdentifiers = otherIdentifiers,
      ),
      locations = locations,
      title = title,
    )

  def createIdentifiedItem = createIdentifiedItemWith()

  def createIdentifiedItems(count: Int) =
    (1 to count).map { _ =>
      createIdentifiedItem
    }.toList

  def createIdentifiableItemWith[I >: IdState.Identifiable](
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    locations: List[Location] = List(defaultLocation)
  ): Item[I] =
    Item(
      id = IdState.Identifiable(sourceIdentifier),
      locations = locations
    )

  def createUnidentifiableItemWith[I >: IdState.Unidentifiable.type](
    locations: List[Location] = List(defaultLocation)): Item[I] =
    Item(id = IdState.Unidentifiable, locations = locations)

  def createPhysicalLocation = createPhysicalLocationWith()

  def createPhysicalLocationWith(locationType: LocationType =
                                   createStoresLocationType,
                                 accessConditions: List[AccessCondition] = Nil,
                                 label: String = "locationLabel") =
    PhysicalLocation(
      locationType = locationType,
      label = label,
      accessConditions = accessConditions
    )

  def createDigitalLocation = createDigitalLocationWith()

  def createImageLocation = createDigitalLocationWith(
    locationType = createImageLocationType
  )

  def createManifestLocation = createDigitalLocationWith(
    locationType = createPresentationLocationType
  )

  def createDigitalLocationWith(
    locationType: LocationType = createPresentationLocationType,
    url: String = defaultLocationUrl,
    license: Option[License] = Some(License.CCBY),
    accessConditions: List[AccessCondition] = Nil) = DigitalLocation(
    locationType = locationType,
    url = url,
    license = license,
    accessConditions = accessConditions
  )

  def createImageLocationType = LocationType("iiif-image")

  def createPresentationLocationType = LocationType("iiif-presentation")

  def createStoresLocationType = LocationType("sgmed")

  def createIdentifiablePhysicalItem =
    createIdentifiableItemWith(locations = List(createPhysicalLocation))

  def createIdentifiedPhysicalItem =
    createIdentifiedItemWith(locations = List(createPhysicalLocation))

  def createDigitalItem =
    createUnidentifiableItemWith(locations = List(createDigitalLocation))

  def createDigitalItemWith(locations: List[Location]) =
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
