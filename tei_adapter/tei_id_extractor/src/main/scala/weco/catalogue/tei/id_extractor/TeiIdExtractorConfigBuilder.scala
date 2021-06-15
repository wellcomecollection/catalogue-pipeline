package weco.catalogue.tei.id_extractor

import com.typesafe.config.Config
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._
import scala.concurrent.duration.FiniteDuration
import scala.compat.java8.DurationConverters._
object TeiIdExtractorConfigBuilder {
  def buildTeiIdExtractorConfig(config: Config) = TeiIdExtractorConfig(
    parallelism = config.requireInt("tei.id_extractor.parallelism"),
    deleteMessageDelay = config.getDuration("tei.id_extractor.delete.delay").toScala)
}
case class TeiIdExtractorConfig(parallelism: Int,
                                deleteMessageDelay: FiniteDuration,
                                teiDirectories: Set[String] = Set("Arabic", "Batak", "Egyptian", "Greek", "Hebrew", "Indic", "Javanese", "Malay"))
