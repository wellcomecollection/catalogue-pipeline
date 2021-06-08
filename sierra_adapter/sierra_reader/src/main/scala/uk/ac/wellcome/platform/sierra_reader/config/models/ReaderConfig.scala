package uk.ac.wellcome.platform.sierra_reader.config.models

import weco.catalogue.source_model.sierra.identifiers.SierraRecordTypes

case class ReaderConfig(
  recordType: SierraRecordTypes.Value,
  fields: String,
  batchSize: Int = 50
)
