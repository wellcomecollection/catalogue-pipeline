package uk.ac.wellcome.platform.ingestor.config.builders

import com.sksamuel.elastic4s.Index
import com.typesafe.config.Config
import uk.ac.wellcome.platform.ingestor.config.models.IngestorConfig
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

import scala.concurrent.duration._

object IngestorConfigBuilder {
  def buildIngestorConfig(config: Config): IngestorConfig = {

    // TODO: Work out how to get a Duration from a Typesafe flag.
    val flushInterval = 1 minute

    val batchSize = config.required[Int]("es.ingest.batchSize")

    val indexName = config.required[String]("es.index")

    IngestorConfig(
      batchSize = batchSize,
      flushInterval = flushInterval,
      index = Index(indexName)
    )
  }
}
