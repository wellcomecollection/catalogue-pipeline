package uk.ac.wellcome.platform.idminter.config.builders

import com.typesafe.config.Config
import scalikejdbc.{ConnectionPool, ConnectionPoolSettings, DB}
import uk.ac.wellcome.platform.idminter.config.models.RDSClientConfig
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

object RDSBuilder {
  def buildDB(config: Config): DB = {

    // Previously this used the config.required[Int] helper, but was found to
    // be broken. See https://github.com/wellcometrust/platform/issues/3824
    val maxSize = getMaxConnections(config)

    val rdsClientConfig = buildRDSClientConfig(config)

    Class.forName("com.mysql.jdbc.Driver")
    ConnectionPool.singleton(
      s"jdbc:mysql://${rdsClientConfig.host}:${rdsClientConfig.port}",
      user = rdsClientConfig.username,
      password = rdsClientConfig.password,
      settings = ConnectionPoolSettings(maxSize = maxSize)
    )
    DB.connect()
  }

  def getMaxConnections(config: Config): Int = {
    val path = "aws.rds.maxConnections"
    if (!config.hasPath(path)) {
      throw new RuntimeException(s"${path} not defined in Config")
    }
    config.getAnyRef(path) match {
      case value: String => value.toInt
      case value: Integer => value
      case _ => throw new RuntimeException(s"${path} is invalid type")
    }
  }

  def buildRDSClientConfig(config: Config): RDSClientConfig = {
    val host = config.required[String]("aws.rds.host")
    val port = config.getOrElse[Int]("aws.rds.port")(default = 3306)
    val username = config.required[String]("aws.rds.username")
    val password = config.required[String]("aws.rds.password")

    RDSClientConfig(
      host = host,
      port = port,
      username = username,
      password = password
    )
  }

}
