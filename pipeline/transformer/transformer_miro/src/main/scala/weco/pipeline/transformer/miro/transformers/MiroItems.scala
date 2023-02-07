package weco.pipeline.transformer.miro.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Item
import weco.catalogue.source_model.miro.MiroSourceOverrides
import weco.pipeline.transformer.miro.source.MiroRecord

trait MiroItems extends MiroLocation {

  def getItems(
    miroRecord: MiroRecord,
    overrides: MiroSourceOverrides
  ): List[Item[IdState.Unminted]] =
    List(
      Item(
        id = IdState.Unidentifiable,
        locations = List(getLocation(miroRecord, overrides))
      )
    )
}
