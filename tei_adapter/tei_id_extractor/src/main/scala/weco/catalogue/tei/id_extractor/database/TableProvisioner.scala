package weco.catalogue.tei.id_extractor.database

import org.flywaydb.core.Flyway

import scala.collection.JavaConverters._

class TableProvisioner(
  rdsClientConfig: RDSClientConfig,
  pathIdConfig: PathIdTableConfig
) {

  def provision(): Unit = {
    val flyway = Flyway.configure()
      .dataSource(
      s"jdbc:mysql://${rdsClientConfig.host}:${rdsClientConfig.port}/${pathIdConfig.database}",
      rdsClientConfig.username,
      rdsClientConfig.password
    )
    .placeholders(
      Map(
        "database" -> pathIdConfig.database,
        "tableName" -> pathIdConfig.tableName
      ).asJava
    )
      .load()

    flyway.migrate()
  }

}

case class PathIdTableConfig(
  database: String,
  tableName: String
)
