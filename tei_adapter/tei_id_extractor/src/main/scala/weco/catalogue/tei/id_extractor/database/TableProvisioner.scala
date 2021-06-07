package weco.catalogue.tei.id_extractor.database

import org.flywaydb.core.Flyway

import scala.collection.JavaConverters._

class TableProvisioner(rdsClientConfig: RDSClientConfig)(database: String, tableName: String) {

  def provision(): Unit = {
    val flyway = new Flyway()
    flyway.setDataSource(
      s"jdbc:mysql://${rdsClientConfig.primaryHost}:${rdsClientConfig.port}/$database",
      rdsClientConfig.username,
      rdsClientConfig.password
    )
    flyway.setPlaceholders(
      Map("database" -> database, "tableName" -> tableName).asJava)
    flyway.migrate()
  }

}
case class RDSClientConfig(
                            primaryHost: String,
                            replicaHost: String,
                            port: Int,
                            username: String,
                            password: String
                          )
case class PathIdTableConfig(
                                   database: String,
                                   tableName: String
                                 )
