package weco.catalogue.tei.id_extractor.database

import com.typesafe.config.Config
import weco.typesafe.config.builders.EnrichConfig._

object PathIdTableBuilder {
  def buildTableConfig(config: Config): PathIdTableConfig = {
    val database = config.requireString("tei.id_extractor.database")
    val tableName = config.requireString("tei.id_extractor.table")

    PathIdTableConfig(
      database = database,
      tableName = tableName
    )
  }

}
