package weco.pipeline.id_minter.config.builders

import scalikejdbc.{ConnectionPool, ConnectionPoolSettings}
import weco.pipeline.id_minter.config.models.RDSClientConfig

object RDSBuilder {

  def buildDB(rdsClientConfig: RDSClientConfig): Unit = {
    val connectionPoolSettings = ConnectionPoolSettings(
      maxSize = rdsClientConfig.maxConnections,
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

}
