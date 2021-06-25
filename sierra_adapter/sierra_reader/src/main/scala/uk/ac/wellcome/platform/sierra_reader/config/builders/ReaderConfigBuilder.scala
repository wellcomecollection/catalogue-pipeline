package uk.ac.wellcome.platform.sierra_reader.config.builders

import com.typesafe.config.Config
import uk.ac.wellcome.platform.sierra_reader.config.models.ReaderConfig
import weco.typesafe.config.builders.EnrichConfig._
import weco.catalogue.source_model.config.SierraRecordTypeBuilder

object ReaderConfigBuilder {
  def buildReaderConfig(config: Config): ReaderConfig = {
    val recordType = SierraRecordTypeBuilder.build(config, name = "reader")

    ReaderConfig(
      recordType = recordType,
      fields = config.requireString("reader.fields"),
      batchSize = config
        .getIntOption("reader.batchSize")
        .getOrElse(10)
    )
  }
}
