package weco.pipeline.transformer.miro.transformers

import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Period
import weco.pipeline.transformer.miro.source.MiroRecord

trait MiroCreatedDate {
  def getCreatedDate(miroRecord: MiroRecord): Option[Period[IdState.Unminted]] =
    if (collectionIsV(miroRecord.imageNumber))
      miroRecord.artworkDate.map(Period(_))
    else
      None

  private def collectionIsV(imageNumber: String): Boolean =
    imageNumber.startsWith("V")
}
