package weco.pipeline.transformer.calm.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.locations.{
  AccessCondition,
  AccessMethod,
  LocationType,
  PhysicalLocation
}
import weco.catalogue.internal_model.work.Item
import weco.catalogue.source_model.calm.CalmRecord
import weco.pipeline.transformer.calm.models.CalmRecordOps

object CalmItems extends CalmRecordOps {
  def apply(record: CalmRecord): List[Item[IdState.Unminted]] =
    List(
      Item(
        title = None,
        locations = List(
          PhysicalLocation(
            locationType = LocationType.ClosedStores,
            label = LocationType.ClosedStores.label,
            accessConditions = createAccessCondition(record)
          )
        )
      )
    )

  private def createAccessCondition(
    record: CalmRecord): List[AccessCondition] = {
    val accessStatus = CalmAccessStatus(record)

    List(
      AccessCondition(
        // Items cannot be requested directly from Calm.  If this record gets merged
        // with a Sierra record, then we'll change the AccessMethod to match.
        method = AccessMethod.NotRequestable,
        status = accessStatus
      )
    ).filterNot(_.isEmpty)
  }
}
