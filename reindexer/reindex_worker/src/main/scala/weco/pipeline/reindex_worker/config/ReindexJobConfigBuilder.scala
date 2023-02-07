package weco.pipeline.reindex_worker.config

import com.typesafe.config.Config
import grizzled.slf4j.Logging
import weco.json.JsonUtil._
import weco.messaging.sns.SNSConfig
import weco.pipeline.reindex_worker.models.ReindexJobConfig
import weco.typesafe.config.builders.EnrichConfig._

import scala.util.{Failure, Success}

object ReindexJobConfigBuilder extends Logging {
  def buildReindexJobConfigMap(
    config: Config
  ): Map[String, ReindexJobConfig[SNSConfig]] =
    buildReindexJobConfigMap(
      config.requireString("reindexer.jobConfig")
    )

  def buildReindexJobConfigMap(
    jsonString: String
  ): Map[String, ReindexJobConfig[SNSConfig]] = {
    val configMap =
      fromJson[Map[String, ReindexJobConfig[SNSConfig]]](jsonString) match {
        case Success(value) => value
        case Failure(err) =>
          throw new RuntimeException(
            s"Unable to parse reindexer.jobConfig: <<$jsonString>> ($err)"
          )
      }

    info(s"Read config map $configMap")
    configMap
  }
}
