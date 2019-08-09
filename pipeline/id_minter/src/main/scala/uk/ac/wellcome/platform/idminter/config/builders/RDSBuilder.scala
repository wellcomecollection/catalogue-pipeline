package uk.ac.wellcome.platform.idminter.config.builders

import scala.util.Try
import com.typesafe.config.{Config, ConfigException}
import scalikejdbc.{ConnectionPool, ConnectionPoolSettings, DB}
import uk.ac.wellcome.platform.idminter.config.models.RDSClientConfig
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

object RDSBuilder {
  def buildDB(config: Config): DB = {

    // Previously this used the config.required[Int] helper, but was found to
    // be broken. See https://github.com/wellcometrust/platform/issues/3824
    val maxSize = intFromConfig(config, "aws.rds.maxConnections")

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

  def buildRDSClientConfig(config: Config): RDSClientConfig = {
    val host = config.required[String]("aws.rds.host")

    // See https://github.com/wellcometrust/platform/issues/3824
    val port = intFromConfig(config, "aws.rds.port", default = Some(3306))

    val username = config.required[String]("aws.rds.username")
    val password = config.required[String]("aws.rds.password")

    RDSClientConfig(
      host = host,
      port = port,
      username = username,
      password = password
    )
  }

  def intFromConfig(config: Config,
                    path: String,
                    default: Option[Int] = None): Int =
    Try(config.getAnyRef(path))
      .map {
        _ match {
          case value: String  => value.toInt
          case value: Integer => value.asInstanceOf[Int]
          case obj =>
            throw new RuntimeException(
              s"$path is invalid type: got $obj (type ${obj.getClass}), expected Int")
        }
      }
      .recover {
        case exc: ConfigException.Missing =>
          default getOrElse {
            throw new RuntimeException(s"${path} not defined in Config")
          }
      }
      .get
}
