package weco.catalogue.tei.id_extractor.database

import com.typesafe.config.Config
import scalikejdbc.{ConnectionPool, ConnectionPoolSettings}
import weco.typesafe.config.builders.EnrichConfig._
import scala.concurrent.duration._

object RDSClientBuilder {
  def buildDB(rdsClientConfig: RDSClientConfig): Unit =
    ConnectionPool.singleton(
      s"jdbc:mysql://${rdsClientConfig.host}:${rdsClientConfig.port}",
      user = rdsClientConfig.username,
      password = rdsClientConfig.password,
      settings = ConnectionPoolSettings(
        maxSize = rdsClientConfig.maxConnections,
        connectionTimeoutMillis = (10 minutes).toMillis
      )
    )

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
      password = password,
      maxConnections = config.requireInt("aws.rds.maxConnections")
    )
  }
}
case class RDSClientConfig(
  host: String,
  port: Int,
  username: String,
  password: String,
  maxConnections: Int
)
