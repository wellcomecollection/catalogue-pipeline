package uk.ac.wellcome.platform.transformer.sierra.transformers

import uk.ac.wellcome.platform.transformer.sierra.source._
import weco.catalogue.internal_model.locations._
import weco.catalogue.source_model.sierra.{SierraBibNumber, SierraItemNumber}

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
        accessConditions = SierraAccessConditions(bibNumber, bibData),
        label = label,
        shelfmark = SierraShelfmark(bibData, itemData)
      )
    } yield physicalLocation
}
