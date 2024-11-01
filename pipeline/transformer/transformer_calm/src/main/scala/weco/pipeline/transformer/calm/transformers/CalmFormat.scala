package weco.pipeline.transformer.calm.transformers

import weco.catalogue.internal_model.work.Format
import weco.catalogue.source_model.calm.CalmRecord
import weco.pipeline.transformer.calm.models.CalmRecordOps

object CalmFormat extends CalmRecordOps {
  def apply(record: CalmRecord): Format = {
    record.get("Material") match {
      case Some("Born-digital archives") => Format.ArchivesDigital
      case _                          => Format.ArchivesAndManuscripts
    }
  }
}
