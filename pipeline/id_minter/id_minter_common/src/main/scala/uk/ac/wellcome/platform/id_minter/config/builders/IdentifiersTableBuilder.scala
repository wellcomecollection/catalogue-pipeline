package uk.ac.wellcome.platform.id_minter.config.builders

import com.typesafe.config.Config
import uk.ac.wellcome.platform.id_minter.config.models.IdentifiersTableConfig
import uk.ac.wellcome.typesafe.config.builders.EnrichConfig._

object IdentifiersTableBuilder {
  def buildConfig(config: Config): IdentifiersTableConfig = {
    val database = config.requireString("aws.rds.identifiers.database")
    val tableName = config.requireString("aws.rds.identifiers.table")

    IdentifiersTableConfig(
      database = database,
      tableName = tableName
    )
  }
}
