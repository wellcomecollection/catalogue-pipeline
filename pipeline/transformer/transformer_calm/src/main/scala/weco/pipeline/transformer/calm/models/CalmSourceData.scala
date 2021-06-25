package weco.pipeline.transformer.calm.models

import weco.catalogue.source_model.calm.CalmRecord

case class CalmSourceData(record: CalmRecord, isDeleted: Boolean = false)
