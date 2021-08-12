package weco.pipeline.sierra_reader.config.builders

import com.typesafe.config.Config
import weco.pipeline.sierra_reader.config.models.ReaderConfig
import weco.sierra.typesafe.SierraRecordTypeBuilder
import weco.typesafe.config.builders.EnrichConfig._

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
