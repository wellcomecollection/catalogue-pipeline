package weco.pipeline.transformer.sierra.transformers

import weco.catalogue.internal_model.locations._
import weco.catalogue.source_model.sierra.rules.{
  SierraItemAccess,
  SierraPhysicalLocationType
}
import weco.sierra.models.data.{SierraBibData, SierraItemData}

trait SierraPhysicalLocation {
  def getPhysicalLocation(
    itemData: SierraItemData,
    bibData: SierraBibData,
    fallbackLocation: Option[(PhysicalLocationType, String)] = None
  ): Option[PhysicalLocation] =
    for {
      sourceLocation <- itemData.location
      (locationType, label) <- {
        SierraPhysicalLocationType
          .fromName(
            id = itemData.id,
            name = sourceLocation.name
          )
          .map {
            case locationType @ LocationType.ClosedStores =>
              (locationType, LocationType.ClosedStores.label)
            case locationType => (locationType, sourceLocation.name)
          }
          .orElse(fallbackLocation)
      }

      (accessCondition, _) = SierraItemAccess(
        location = Some(locationType),
        itemData = itemData
      )

      physicalLocation = PhysicalLocation(
        locationType = locationType,
        accessConditions = List(accessCondition),
        label = label,
        shelfmark = SierraShelfmark(bibData, itemData)
      )
    } yield physicalLocation
}
