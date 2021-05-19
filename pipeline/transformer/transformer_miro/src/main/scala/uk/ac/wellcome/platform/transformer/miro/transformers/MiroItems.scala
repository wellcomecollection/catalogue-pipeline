package uk.ac.wellcome.platform.transformer.miro.transformers

import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Item
import weco.catalogue.source_model.miro.MiroSourceOverrides

trait MiroItems extends MiroLocation {

  def getItems(miroRecord: MiroRecord, overrides: MiroSourceOverrides): List[Item[IdState.Unminted]] =
    List(
      Item(
        id = IdState.Unidentifiable,
        locations = List(getLocation(miroRecord, overrides))))
}
