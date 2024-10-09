package weco.pipeline.id_minter.database

import org.flywaydb.core.Flyway
import weco.pipeline.id_minter.config.models.RDSClientConfig

import scala.collection.JavaConverters._

class TableProvisioner(rdsClientConfig: RDSClientConfig) {

  def provision(database: String, tableName: String): Unit = {
    val flyway = new Flyway()
    flyway.setDataSource(
      s"jdbc:mysql://${rdsClientConfig.primaryHost}:${rdsClientConfig.port}/$database",
      rdsClientConfig.username,
      rdsClientConfig.password
    )
    flyway.setPlaceholders(
      Map("database" -> database, "tableName" -> tableName).asJava
    )

    flyway.migrate()
  }

}
