package weco.pipeline.id_minter.config.models

import com.typesafe.config.Config
import weco.typesafe.config.builders.EnrichConfig._

case class RDSClientConfig(
  primaryHost: String,
  replicaHost: String,
  port: Int,
  username: String,
  password: String,
  maxConnections: Int
)

object RDSClientConfig {
  def apply(config: Config): RDSClientConfig = {
    val primaryHost = config.requireString("aws.rds.primary_host")
    val replicaHost = config.requireString("aws.rds.replica_host")

    val port = config
      .getIntOption("aws.rds.port")
      .getOrElse(3306)

    val username = config.requireString("aws.rds.username")
    val password = config.requireString("aws.rds.password")

    RDSClientConfig(
      primaryHost = primaryHost,
      replicaHost = replicaHost,
      port = port,
      username = username,
      password = password,
      maxConnections = config.requireInt("aws.rds.maxConnections")
    )
  }
}
