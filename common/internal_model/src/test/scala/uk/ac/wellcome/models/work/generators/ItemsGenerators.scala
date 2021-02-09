package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal._

trait ItemsGenerators extends IdentifiersGenerators with LocationGenerators {

  def createIdentifiedItemWith[I >: IdState.Identified](
    canonicalId: String = createCanonicalId,
    sourceIdentifier: SourceIdentifier = createSourceIdentifier,
    otherIdentifiers: List[SourceIdentifier] = Nil,
    locations: List[Location] = List(createDigitalLocation),
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
    locations: List[Location] = List(createDigitalLocation)
  ): Item[I] =
    Item(
      id = IdState.Identifiable(sourceIdentifier),
      locations = locations
    )

  def createUnidentifiableItemWith(
    locations: List[Location] = List(createDigitalLocation)): Item[IdState.Unidentifiable.type] =
    Item(id = IdState.Unidentifiable, locations = locations)

  def createUnidentifiableItem: Item[IdState.Unidentifiable.type] =
    createUnidentifiableItemWith()

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
}
