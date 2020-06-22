package uk.ac.wellcome.platform.sierra_reader.config.builders

import com.typesafe.config.Config
import uk.ac.wellcome.platform.sierra_reader.config.models.ReaderConfig
import uk.ac.wellcome.platform.sierra_reader.models.SierraResourceTypes.{
  bibs,
  items
}
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

object ReaderConfigBuilder {
  def buildReaderConfig(config: Config): ReaderConfig = {
    val resourceType = config.requireString("reader.resourceType") match {
      case s: String if s == bibs.toString  => bibs
      case s: String if s == items.toString => items
      case s: String =>
        throw new IllegalArgumentException(
          s"$s is not a valid Sierra resource type")
    }

    ReaderConfig(
      resourceType = resourceType,
      fields = config.requireString("reader.fields"),
      batchSize =
        config
          .getIntOption("reader.batchSize")
          .getOrElse(10)
    )
  }
}
