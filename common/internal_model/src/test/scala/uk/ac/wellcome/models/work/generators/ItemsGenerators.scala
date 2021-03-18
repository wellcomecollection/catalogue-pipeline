package uk.ac.wellcome.models.work.generators

import uk.ac.wellcome.models.work.internal._
import weco.catalogue.internal_model.generators.IdentifiersGenerators
import weco.catalogue.internal_model.identifiers.{IdState, SourceIdentifier}

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

  def createUnidentifiableItemWith(
    locations: List[Location] = List(createDigitalLocation))
    : Item[IdState.Unidentifiable.type] =
    Item(id = IdState.Unidentifiable, locations = locations)

  def createUnidentifiableItem: Item[IdState.Unidentifiable.type] =
    createUnidentifiableItemWith()

  def createIdentifiedPhysicalItem: Item[IdState.Identified] =
    createIdentifiedItemWith(locations = List(createPhysicalLocation))

  def createDigitalItem: Item[IdState.Unidentifiable.type] =
    createUnidentifiableItemWith(locations = List(createDigitalLocation))

  def createDigitalItemWith(
    accessStatus: AccessStatus): Item[IdState.Unidentifiable.type] =
    createDigitalItemWith(
      locations = List(
        createDigitalLocationWith(accessConditions =
          List(createAccessConditionWith(status = Some(accessStatus))))))

  def createDigitalItemWith(
    locations: List[Location]): Item[IdState.Unidentifiable.type] =
    createUnidentifiableItemWith(locations = locations)

  def createDigitalItemWith(
    license: Option[License]): Item[IdState.Unidentifiable.type] =
    createUnidentifiableItemWith(
      locations = List(createDigitalLocationWith(license = license))
    )

  def createCalmItem: Item[IdState.Unidentifiable.type] =
    createUnidentifiableItemWith(
      locations = List(
        createPhysicalLocationWith(
          locationType = LocationType.ClosedStores,
          label = LocationType.ClosedStores.label
        )
      )
    )
}
