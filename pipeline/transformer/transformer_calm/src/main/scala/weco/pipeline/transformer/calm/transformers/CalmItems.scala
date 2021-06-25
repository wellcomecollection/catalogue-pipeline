package weco.pipeline.transformer.calm.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.locations.{LocationType, PhysicalLocation}
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
            label = LocationType.ClosedStores.label
          )
        )
      )
    )
}
