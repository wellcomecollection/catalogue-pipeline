package uk.ac.wellcome.platform.transformer.sierra.transformers

import weco.catalogue.internal_model.locations._
import weco.catalogue.source_model.sierra.rules.SierraPhysicalLocationType
import weco.catalogue.source_model.sierra.{
  SierraBibData,
  SierraBibNumber,
  SierraItemData,
  SierraItemNumber
}

trait SierraLocation {
  def getPhysicalLocation(
    bibNumber: SierraBibNumber,
    itemNumber: SierraItemNumber,
    itemData: SierraItemData,
    bibData: SierraBibData,
    fallbackLocation: Option[(PhysicalLocationType, String)] = None)
    : Option[PhysicalLocation] =
    for {
      sourceLocation <- itemData.location

      (locationType, label) <- {
        val parsedLocationType =
          SierraPhysicalLocationType.fromName(itemNumber, sourceLocation.name)

        (parsedLocationType, fallbackLocation) match {
          case (Some(locationType), _) =>
            val label = locationType match {
              case LocationType.ClosedStores => LocationType.ClosedStores.label
              case _                         => sourceLocation.name
            }

            Some((locationType, label))

          case (_, Some(fallbackLocation)) =>
            Some(fallbackLocation)

          case _ => None
        }
      }

      physicalLocation = PhysicalLocation(
        locationType = locationType,
        accessConditions = SierraAccessStatus
          .forBib(bibNumber, bibData)
          .map { status =>
            AccessCondition(status)
          }
          .toList,
        label = label,
        shelfmark = SierraShelfmark(bibData, itemData)
      )
    } yield physicalLocation
}
