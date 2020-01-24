package uk.ac.wellcome.platform.ingestor.config.builders

import com.sksamuel.elastic4s.Index
import com.typesafe.config.{Config, ConfigException}
import uk.ac.wellcome.platform.ingestor.config.models.IngestorConfig
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

import scala.concurrent.duration._
import scala.util.Try

object IngestorConfigBuilder {
  def buildIngestorConfig(config: Config): IngestorConfig = {

    // TODO: Work out how to get a Duration from a Typesafe flag.
    val flushInterval = 1 minute

    val batchSize = intFromConfig(config, "es.ingest.batchSize")

    val indexName = config.required[String]("es.index")

    IngestorConfig(
      batchSize = batchSize,
      flushInterval = flushInterval,
      index = Index(indexName)
    )
  }

  def intFromConfig(config: Config,
                    path: String,
                    default: Option[Int] = None): Int =
    Try(config.getAnyRef(path))
      .map {
        case value: String  => value.toInt
        case value: Integer => value.asInstanceOf[Int]
        case obj =>
          throw new RuntimeException(
            s"$path is invalid type: got $obj (type ${obj.getClass}), expected Int")
      }
      .recover {
        case exc: ConfigException.Missing =>
          default getOrElse {
            throw new RuntimeException(s"${path} not defined in Config")
          }
      }
      .get
}
