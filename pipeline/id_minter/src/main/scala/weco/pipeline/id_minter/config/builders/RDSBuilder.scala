package weco.pipeline.id_minter.config.builders

import com.typesafe.config.Config
import scalikejdbc.{ConnectionPool, ConnectionPoolSettings}
import weco.pipeline.id_minter.config.models.RDSClientConfig
import weco.typesafe.config.builders.EnrichConfig._

object RDSBuilder {
  def buildDB(config: Config): Unit = {
    val maxConnections = config.requireInt("aws.rds.maxConnections")

    val rdsClientConfig = buildRDSClientConfig(config)

    buildDB(
      maxConnections = maxConnections,
      rdsClientConfig = rdsClientConfig
    )
  }

  def buildDB(maxConnections: Int, rdsClientConfig: RDSClientConfig): Unit = {
    val connectionPoolSettings = ConnectionPoolSettings(
      maxSize = maxConnections,
      connectionTimeoutMillis = 120000L
    )

    ConnectionPool.add(
      name = 'primary,
      url =
        s"jdbc:mysql://${rdsClientConfig.primaryHost}:${rdsClientConfig.port}/identifiers",
      user = rdsClientConfig.username,
      password = rdsClientConfig.password,
      settings = connectionPoolSettings
    )
    ConnectionPool.add(
      name = 'replica,
      url =
        s"jdbc:mysql://${rdsClientConfig.replicaHost}:${rdsClientConfig.port}",
      user = rdsClientConfig.username,
      password = rdsClientConfig.password,
      settings = connectionPoolSettings
    )
  }

  def buildRDSClientConfig(config: Config): RDSClientConfig = {
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
      password = password
    )
  }
}
