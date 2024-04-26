package weco.pipeline.transformer.marc_common.transformers

import grizzled.slf4j.Logging
import weco.catalogue.internal_model.identifiers.IdState
import weco.catalogue.internal_model.work.Genre
import weco.pipeline.transformer.marc_common.models.MarcRecord

object MarcGenres extends MarcDataTransformer with Logging {

  override type Output = Seq[Genre[IdState.Unminted]]

  override def apply(record: MarcRecord): Output = {
    record.fieldsWithTags("655").flatMap(MarcGenre(_)).distinct
  }

}
