package uk.ac.wellcome.platform.transformer.miro.transformers

import uk.ac.wellcome.models.work.internal.Period
import uk.ac.wellcome.platform.transformer.miro.source.MiroRecord
import weco.catalogue.internal_model.identifiers.IdState

trait MiroCreatedDate {
  def getCreatedDate(miroRecord: MiroRecord): Option[Period[IdState.Unminted]] =
    if (collectionIsV(miroRecord.imageNumber))
      miroRecord.artworkDate.map(Period(_))
    else
      None

  private def collectionIsV(imageNumber: String): Boolean =
    imageNumber.startsWith("V")
}
