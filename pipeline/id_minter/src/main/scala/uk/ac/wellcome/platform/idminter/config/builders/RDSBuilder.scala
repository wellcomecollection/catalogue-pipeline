package uk.ac.wellcome.platform.idminter.config.builders

import scala.util.Try
import com.typesafe.config.{Config, ConfigException}
import scalikejdbc.{ConnectionPool, ConnectionPoolSettings}
import uk.ac.wellcome.platform.idminter.config.models.RDSClientConfig
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

object RDSBuilder {
  def buildDB(config: Config): Unit = {

    val maxSize = config.requireInt("aws.rds.maxConnections")

    val rdsClientConfig = buildRDSClientConfig(config)

    Class.forName("com.mysql.jdbc.Driver")
    ConnectionPool.singleton(
      s"jdbc:mysql://${rdsClientConfig.host}:${rdsClientConfig.port}",
      user = rdsClientConfig.username,
      password = rdsClientConfig.password,
      settings = ConnectionPoolSettings(maxSize = maxSize)
    )
  }

  def buildRDSClientConfig(config: Config): RDSClientConfig = {
    val host = config.requireString("aws.rds.host")

    val port = config
      .getIntOption("aws.rds.port")
      .getOrElse(3306)

    val username = config.requireString("aws.rds.username")
    val password = config.requireString("aws.rds.password")

    RDSClientConfig(
      host = host,
      port = port,
      username = username,
      password = password
    )
  }
}
