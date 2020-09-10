package uk.ac.wellcome.platform.transformer.miro.transformers

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord

trait MiroItems extends MiroLocation {

  def getItems(miroRecord: MiroRecord): List[Item[IdState.Unminted]] =
    List(Item(id = IdState.Unidentifiable, locations = List(getLocation(miroRecord))))
}
