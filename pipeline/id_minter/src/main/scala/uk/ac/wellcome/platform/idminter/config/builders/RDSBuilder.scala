package uk.ac.wellcome.platform.idminter.config.builders

import com.typesafe.config.Config
import scalikejdbc.{ConnectionPool, ConnectionPoolSettings}
import uk.ac.wellcome.platform.idminter.config.models.RDSClientConfig
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

object RDSBuilder {
  def buildDB(config: Config): Unit = {

    val maxSize = config.requireInt("aws.rds.maxConnections")

    val rdsClientConfig = buildRDSClientConfig(config)
    val connectionPoolSettings = ConnectionPoolSettings(
      maxSize = maxSize,
      connectionTimeoutMillis = 120000L
    )

    ConnectionPool.add(
      name = 'primary,
      url =
        s"jdbc:mysql://${rdsClientConfig.primaryHost}:${rdsClientConfig.port}",
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
