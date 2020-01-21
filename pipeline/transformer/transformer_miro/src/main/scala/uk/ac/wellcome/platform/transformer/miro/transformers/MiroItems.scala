package uk.ac.wellcome.platform.transformer.miro.transformers

import uk.ac.wellcome.models.work.internal._
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord

trait MiroItems extends MiroLocations {

  def getItems(miroRecord: MiroRecord): List[Item[Unminted]] =
    List(Item(id = Unidentifiable, locations = getLocations(miroRecord)))
}
