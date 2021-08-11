package weco.catalogue.source_model.config

import com.typesafe.config.Config
import weco.sierra.models.identifiers.SierraRecordTypes
import weco.typesafe.config.builders.EnrichConfig._

object SierraRecordTypeBuilder {
  def build(config: Config, name: String): SierraRecordTypes.Value =
    config.requireString(s"$name.recordType") match {
      case s: String if s == bibs.toString     => bibs
      case s: String if s == items.toString    => items
      case s: String if s == holdings.toString => holdings
      case s: String if s == orders.toString   => orders
      case s: String =>
        throw new IllegalArgumentException(
          s"$s is not a valid Sierra record type")
    }
}
