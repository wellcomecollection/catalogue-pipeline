package uk.ac.wellcome.platform.transformer.miro.transformers

import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Item

trait MiroItems extends MiroLocation {

  def getItems(miroRecord: MiroRecord): List[Item[IdState.Unminted]] =
    List(
      Item(
        id = IdState.Unidentifiable,
        locations = List(getLocation(miroRecord))))
}
