package weco.pipeline.transformer.calm.transformers

import weco.catalogue.source_model.calm.CalmRecord
import weco.pipeline.transformer.calm.models.CalmRecordOps

object CalmAlternativeTitles extends CalmRecordOps {
  def apply(record: CalmRecord): List[String] =
    record.getList("Alternative_Title")
}
